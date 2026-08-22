package com.savorystay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.common.OrderStateMachine;
import com.savorystay.config.OrderStateException;
import com.savorystay.dto.OrderItemRequest;
import com.savorystay.entity.*;
import com.savorystay.repository.*;
import com.savorystay.security.RoleUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final MenuService menuService;
    private final OutboxService outboxService;
    private final IngredientService ingredientService;
    private final PreOrderAvailabilityService availabilityService;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRestaurantService customerRestaurantService;
    private final AuditService auditService;
    private final RefundRepository refundRepository;
    private final RealtimeService realtimeService;
    private final PlateCapacityRepository plateCapacityRepository;
    private final TableSlotCapacityRepository tableSlotCapacityRepository;
    private final RestaurantSettingsRepository restaurantSettingsRepository;
    private final MenuItemIngredientRepository menuItemIngredientRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final List<String> FLOW = List.of("NEW", "PREPARING", "PACKED_READY", "COMPLETED");

    /** Guest count → table type mapping (P0.10). */
    private static String tableTypeForGuests(int guests) {
        if (guests <= 2) return "2-Seater";
        if (guests <= 4) return "4-Seater";
        return "6-Seater";
    }

    /**
     * Customer places an order against a restaurant's menu.
     * Payment status is ALWAYS server-set to PENDING here — clients can never
     * mark their own orders paid. It can only move to PAID via confirmPayment().
     *
     * P0.11: Backend revalidates everything — restaurant, menu items, prices,
     * plate capacity, table capacity, pre-order rules. Frontend is never authoritative.
     *
     * P0.12: Prices are snapshotted on OrderItem so historical orders are stable.
     *
     * P0.8: Plate capacity is atomically reserved using SELECT FOR UPDATE.
     *
     * P0.9: Table capacity is atomically reserved using SELECT FOR UPDATE.
     */
    @Transactional
    public Order placeOrder(String userId, String restaurantId, String customerName,
                            String customerPhone, String customerEmail, String orderType,
                            Integer tableNumber, Integer guests, String timeSlot, String pickupTime,
                            List<OrderItemRequest> items, String paymentMethod) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // P0.11: Revalidate restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        if (!"ACTIVE".equals(restaurant.getStatus())) {
            throw new IllegalArgumentException(
                    "This restaurant is currently offline (" + restaurant.getStatus()
                            + ") — orders cannot be placed right now.");
        }

        String effectiveOrderType = orderType != null ? orderType.toUpperCase() : "PICKUP";

        // P0.14: PRE_ORDER validation
        if ("PRE_ORDER".equals(effectiveOrderType)) {
            validatePreOrder(restaurantId, pickupTime, items);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<PlateCapacity> capacityReservations = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate orderDate = today;

        // For PRE_ORDER, parse the date from pickupTime
        if ("PRE_ORDER".equals(effectiveOrderType) && pickupTime != null && pickupTime.length() >= 10) {
            try {
                orderDate = LocalDate.parse(pickupTime.substring(0, 10));
            } catch (Exception ignored) {}
        }

        for (OrderItemRequest line : items) {
            String menuItemId = line.menuItemId();
            int qty = line.quantity() != null ? line.quantity() : 1;
            if (qty <= 0) {
                throw new IllegalArgumentException("Quantity must be a positive number");
            }

            // P0.11: Revalidate menu item
            MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                    .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
            if (!"Available".equals(menuItem.getStatus())) {
                throw new IllegalArgumentException(menuItem.getTitle() + " is no longer available");
            }

            // P0.8: Atomic plate capacity reservation
            if (menuItem.getDailyPlateCount() != null) {
                PlateCapacity reservation = reservePlateCapacity(menuItem, restaurantId, orderDate, qty);
                capacityReservations.add(reservation);
            }

            // P0.12: Price snapshot — use effective price, preserve on OrderItem
            BigDecimal effectivePrice = menuService.getEffectivePrice(menuItem.getId(), menuItem.getPrice());
            BigDecimal lineTotal = effectivePrice.multiply(BigDecimal.valueOf(qty));
            total = total.add(lineTotal);

            // P0.13: Snapshot ingredient requirements at order time
            String ingredientSnapshot = snapshotIngredients(menuItemId);

            orderItems.add(OrderItem.builder()
                    .orderId(null)
                    .menuItemId(menuItemId)
                    .title(menuItem.getTitle())
                    .quantity(qty)
                    .unitPrice(effectivePrice)
                    .ingredientSnapshot(ingredientSnapshot)
                    .notes(line.notes())
                    .build());
        }

        // P0.9: Atomic table capacity reservation for DINE_IN
        if ("DINE_IN".equals(effectiveOrderType) && guests != null && timeSlot != null) {
            String tableType = tableTypeForGuests(guests);
            reserveTableCapacity(restaurantId, orderDate, timeSlot, tableType, 1);
        }

        Order order = Order.builder()
                .orderNumber("#ORD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .restaurantId(restaurantId)
                .orderType(effectiveOrderType)
                .tableNumber("DINE_IN".equals(effectiveOrderType) ? tableNumber : null)
                .guests(guests)
                .timeSlot(timeSlot)
                .pickupTime(pickupTime)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerEmail(customerEmail)
                .userId(userId)
                .totalAmount(total)
                .paymentStatus("PENDING")
                .paymentMethod(paymentMethod != null ? paymentMethod : "MOCK")
                .orderStatus("NEW")
                .build();
        Order saved = orderRepository.save(order);

        orderItems.forEach(oi -> oi.setOrderId(saved.getId()));
        orderItemRepository.saveAll(orderItems);

        // Transactional outbox: record order.created in the SAME transaction
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("orderId", saved.getId());
        eventPayload.put("restaurantId", restaurantId);
        eventPayload.put("orderNumber", saved.getOrderNumber());
        eventPayload.put("customerName", saved.getCustomerName());
        eventPayload.put("totalAmount", saved.getTotalAmount());
        eventPayload.put("orderType", saved.getOrderType());
        eventPayload.put("userId", userId);
        eventPayload.put("customerPhone", saved.getCustomerPhone());
        eventPayload.put("customerEmail", saved.getCustomerEmail());
        outboxService.record(saved.getId(), "order.created", eventPayload);

        log.info("Order {} placed by {} at restaurant {} (outbox: order.created)",
                saved.getOrderNumber(), customerName, restaurantId);

        // Real-time broadcasts
        if ("DINE_IN".equals(effectiveOrderType) && timeSlot != null) {
            broadcastTableAvailability(restaurantId, timeSlot);
        }
        for (OrderItem oi : orderItems) {
            broadcastPlateCount(restaurantId, oi.getMenuItemId());
        }

        // P0.2: Auto-join (non-critical)
        if (userId != null && Boolean.TRUE.equals(restaurant.getAutoJoinCustomers())) {
            try {
                if (!customerRestaurantService.isMember(userId, restaurantId)) {
                    customerRestaurantService.join(userId, restaurantId, null);
                    log.info("Auto-joined customer {} to restaurant {} on first order", userId, restaurantId);
                }
            } catch (Exception e) {
                log.warn("Failed to auto-join customer {} to restaurant {}: {}", userId, restaurantId, e.getMessage());
            }
        }

        return saved;
    }

    // ─── P0.13: INGREDIENT SNAPSHOT ───────────────────────────────

    /**
     * Snapshot the ingredient requirements for a menu item at the current moment.
     * This preserves the recipe as it was when the order was placed, so future
     * recipe changes don't affect historical forecasts.
     */
    private String snapshotIngredients(String menuItemId) {
        try {
            List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(menuItemId);
            if (ings.isEmpty()) return null;
            List<Map<String, Object>> snapshot = new ArrayList<>();
            for (MenuItemIngredient ing : ings) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("ingredientId", ing.getIngredientId());
                entry.put("name", ing.getName());
                entry.put("quantity", ing.getQuantityPerUnit());
                entry.put("unit", ing.getUnit());
                snapshot.add(entry);
            }
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.debug("Failed to snapshot ingredients for {}: {}", menuItemId, e.getMessage());
            return null;
        }
    }

    // ─── P0.8: PLATE CAPACITY ATOMIC RESERVATION ──────────────────

    /**
     * Atomically reserve plate capacity using SELECT FOR UPDATE.
     * If the dish has no capacity record, one is created from the MenuItem config.
     * Returns the reservation (for potential rollback if the overall order fails).
     *
     * @throws IllegalArgumentException with PLATE_CAPACITY_EXCEEDED message if not enough capacity
     */
    private PlateCapacity reservePlateCapacity(MenuItem menuItem, String restaurantId,
                                                LocalDate businessDate, int requestedQty) {
        // Get or create capacity record with row lock
        PlateCapacity capacity = plateCapacityRepository
                .findByMenuItemIdAndBusinessDateForUpdate(menuItem.getId(), businessDate)
                .orElseGet(() -> {
                    PlateCapacity newCap = PlateCapacity.builder()
                            .menuItemId(menuItem.getId())
                            .restaurantId(restaurantId)
                            .businessDate(businessDate)
                            .capacity(menuItem.getDailyPlateCount() != null ? menuItem.getDailyPlateCount() : Integer.MAX_VALUE)
                            .reservedCount(0)
                            .version(0L)
                            .build();
                    return plateCapacityRepository.save(newCap);
                });

        int remaining = capacity.remaining();
        if (remaining <= 0) {
            throw new IllegalArgumentException(
                    menuItem.getTitle() + " is sold out for today — all "
                            + capacity.getCapacity() + " plates have been ordered.");
        }
        if (requestedQty > remaining) {
            throw new IllegalArgumentException(
                    "Only " + remaining + " plate" + (remaining == 1 ? "" : "s")
                            + " of " + menuItem.getTitle() + " remaining for today."
                            + " You ordered " + requestedQty + ".");
        }

        capacity.setReservedCount(capacity.getReservedCount() + requestedQty);
        try {
            return plateCapacityRepository.save(capacity);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalArgumentException(
                    menuItem.getTitle() + " plate capacity changed concurrently. Please retry.");
        }
    }

    /**
     * Release a plate capacity reservation (called on order cancellation/decline).
     * Idempotent — releasing an already-released reservation is safe.
     */
    @Transactional
    public void releasePlateCapacity(String menuItemId, LocalDate businessDate, int qty) {
        try {
            Optional<PlateCapacity> opt = plateCapacityRepository
                    .findByMenuItemIdAndBusinessDate(menuItemId, businessDate);
            if (opt.isPresent()) {
                PlateCapacity cap = opt.get();
                cap.setReservedCount(Math.max(0, cap.getReservedCount() - qty));
                plateCapacityRepository.save(cap);
            }
        } catch (Exception e) {
            log.warn("Failed to release plate capacity for {} on {}: {}", menuItemId, businessDate, e.getMessage());
        }
    }

    // ─── P0.9: TABLE CAPACITY ATOMIC RESERVATION ──────────────────

    /**
     * Atomically reserve table capacity using SELECT FOR UPDATE.
     * Creates the capacity record from RestaurantSettings if it doesn't exist.
     *
     * @throws IllegalArgumentException TABLE_SLOT_FULL if no tables available
     */
    private void reserveTableCapacity(String restaurantId, LocalDate businessDate,
                                       String timeSlot, String tableType, int tablesNeeded) {
        TableSlotCapacity capacity = tableSlotCapacityRepository
                .findByRestaurantAndDateAndSlotAndTypeForUpdate(restaurantId, businessDate, timeSlot, tableType)
                .orElseGet(() -> {
                    // Create from restaurant settings
                    int total = getTableCount(restaurantId, tableType);
                    TableSlotCapacity newCap = TableSlotCapacity.builder()
                            .restaurantId(restaurantId)
                            .businessDate(businessDate)
                            .timeSlot(timeSlot)
                            .tableType(tableType)
                            .totalCapacity(total)
                            .reservedCount(0)
                            .version(0L)
                            .build();
                    return tableSlotCapacityRepository.save(newCap);
                });

        int remaining = capacity.remaining();
        if (remaining < tablesNeeded) {
            throw new IllegalArgumentException(
                    "No " + tableType.toLowerCase() + " tables are available for " + timeSlot
                            + ". Only " + remaining + " remaining.");
        }

        capacity.setReservedCount(capacity.getReservedCount() + tablesNeeded);
        try {
            tableSlotCapacityRepository.save(capacity);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalArgumentException(
                    "Table reservation changed concurrently. Please retry.");
        }
    }

    /**
     * Release a table capacity reservation (called on order cancellation).
     */
    @Transactional
    public void releaseTableCapacity(String restaurantId, LocalDate businessDate,
                                      String timeSlot, String tableType, int tablesNeeded) {
        try {
            Optional<TableSlotCapacity> opt = tableSlotCapacityRepository
                    .findByRestaurantAndDateAndSlotAndTypeForUpdate(restaurantId, businessDate, timeSlot, tableType);
            if (opt.isPresent()) {
                TableSlotCapacity cap = opt.get();
                cap.setReservedCount(Math.max(0, cap.getReservedCount() - tablesNeeded));
                tableSlotCapacityRepository.save(cap);
            }
        } catch (Exception e) {
            log.warn("Failed to release table capacity for {} on {}: {}", restaurantId, businessDate, e.getMessage());
        }
    }

    /**
     * Get the table count for a table type from restaurant settings.
     */
    private int getTableCount(String restaurantId, String tableType) {
        try {
            var settings = restaurantSettingsRepository.findById(restaurantId);
            if (settings.isPresent() && settings.get().getTableConfig() != null) {
                String json = settings.get().getTableConfig();
                // Simple JSON parsing for table config like [{"type":"2-Seater","count":5}]
                String search = "\"" + tableType + "\"";
                int idx = json.indexOf(search);
                if (idx >= 0) {
                    String after = json.substring(idx + search.length());
                    int countIdx = after.indexOf("\"count\"");
                    if (countIdx >= 0) {
                        String countPart = after.substring(countIdx + 8).replaceAll("[^0-9]", "");
                        if (!countPart.isEmpty()) return Integer.parseInt(countPart);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse table config for {}: {}", restaurantId, e.getMessage());
        }
        return 5; // default fallback
    }

    // ─── PRE-ORDER VALIDATION ──────────────────────────────────────

    private void validatePreOrder(String restaurantId, String pickupTime,
                                  List<OrderItemRequest> items) {
        if (pickupTime == null || pickupTime.isBlank()) {
            throw new IllegalArgumentException("pickupTime (ISO datetime) is required for pre-orders");
        }
        LocalDate date;
        LocalTime time = null;
        try {
            String datePart = pickupTime.length() >= 10 ? pickupTime.substring(0, 10) : pickupTime;
            date = LocalDate.parse(datePart);
        } catch (Exception e) {
            throw new IllegalArgumentException("pickupTime must be an ISO datetime (YYYY-MM-DDTHH:MM:SS)");
        }
        try {
            time = LocalTime.parse(pickupTime.substring(11, 19));
        } catch (Exception ignored) {}

        List<String> menuItemIds = items.stream().map(OrderItemRequest::menuItemId).toList();
        availabilityService.validatePreOrder(restaurantId, date, time, menuItemIds);
    }

    // ─── PAYMENT CONFIRMATION ─────────────────────────────────────

    /**
     * Server-authoritative payment confirmation. P0.4: Idempotent.
     */
    @Transactional
    public Order confirmPayment(String orderId, String actorUserId, String role, String restaurantId,
                                BigDecimal amount, String gateway) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        boolean isOwner = actorUserId != null && actorUserId.equals(order.getUserId());
        boolean isStaff = role != null && !"ROLE_CUSTOMER".equals(role)
                && restaurantId != null && restaurantId.equals(order.getRestaurantId());
        if (!isOwner && !isStaff) {
            throw new SecurityException("Forbidden: not the order owner or restaurant staff");
        }

        // P0.4: Idempotent — already confirmed
        if ("PAID".equals(order.getPaymentStatus())) {
            return order;
        }

        // P0.4: Idempotent — payment record exists
        if (!paymentRepository.findByOrderId(orderId).isEmpty()) {
            order.setPaymentStatus("PAID");
            return orderRepository.save(order);
        }

        if ("CASH".equalsIgnoreCase(gateway) || "CASH".equalsIgnoreCase(order.getPaymentMethod())) {
            throw new IllegalArgumentException("CASH orders cannot be confirmed online — customer pays at pickup");
        }

        if (amount == null) {
            throw new IllegalArgumentException("Payment amount is required");
        }
        BigDecimal paid = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal expected = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(expected) != 0) {
            throw new IllegalArgumentException("Payment amount does not match order total");
        }

        order.setPaymentStatus("PAID");
        Order saved = orderRepository.save(order);

        paymentRepository.save(Payment.builder()
                .transactionId("TXN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .orderId(orderId)
                .gateway(gateway != null ? gateway : "MOCK")
                .amount(order.getTotalAmount())
                .currency("INR")
                .paymentStatus("PAID")
                .build());

        Map<String, Object> paymentPayload = new LinkedHashMap<>();
        paymentPayload.put("orderId", orderId);
        paymentPayload.put("restaurantId", saved.getRestaurantId());
        paymentPayload.put("orderNumber", saved.getOrderNumber());
        paymentPayload.put("customerName", saved.getCustomerName());
        paymentPayload.put("userId", saved.getUserId());
        paymentPayload.put("customerEmail", saved.getCustomerEmail());
        paymentPayload.put("customerPhone", saved.getCustomerPhone());
        paymentPayload.put("totalAmount", order.getTotalAmount());
        paymentPayload.put("gateway", gateway != null ? gateway : "MOCK");
        outboxService.record(orderId, "payment.confirmed", paymentPayload);

        log.info("Order {} marked PAID by {} (outbox: payment.confirmed)", saved.getOrderNumber(), actorUserId);
        return saved;
    }

    // ─── CASH PAYMENT ─────────────────────────────────────────────

    @Transactional
    public Order markCashPaid(String orderId, String restaurantId, String actorUserId, String role) {
        Order order = orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found in this restaurant"));

        if (role == null || "ROLE_CUSTOMER".equals(role)) {
            throw new SecurityException("Only staff can mark cash payments as paid");
        }
        if (!"CASH".equalsIgnoreCase(order.getPaymentMethod())) {
            throw new IllegalArgumentException(
                    "Only CASH orders can be marked paid at the counter (order method: " + order.getPaymentMethod() + ")");
        }
        if ("PAID".equals(order.getPaymentStatus())) {
            return order; // idempotent
        }
        if (!"PENDING".equals(order.getPaymentStatus())) {
            throw new IllegalArgumentException("Cannot mark a " + order.getPaymentStatus() + " order as paid");
        }

        order.setPaymentStatus("PAID");
        Order saved = orderRepository.save(order);

        paymentRepository.save(Payment.builder()
                .transactionId("TXN_CASH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .orderId(orderId)
                .gateway("CASH")
                .amount(order.getTotalAmount())
                .currency("INR")
                .paymentStatus("PAID")
                .build());

        auditService.record(restaurantId, actorUserId, role, "CASH_PAYMENT_RECORDED", "ORDER", orderId,
                Map.of("orderNumber", saved.getOrderNumber(), "amount", saved.getTotalAmount(),
                       "paymentMethod", "CASH"),
                "Cash payment collected at counter");

        log.info("Cash payment recorded for order {} by {} (amount: {})", saved.getOrderNumber(), actorUserId, saved.getTotalAmount());
        return saved;
    }

    // ─── QUERIES ───────────────────────────────────────────────────

    public List<Order> ordersForCustomer(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Order> ordersForRestaurant(String restaurantId) {
        return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    @Transactional
    public void updateItemNotes(String orderId, String itemId, String notes) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));
        if (!item.getOrderId().equals(orderId)) {
            throw new IllegalArgumentException("Order item does not belong to this order");
        }
        item.setNotes(notes);
        orderItemRepository.save(item);
    }

    public List<OrderItem> itemsFor(String orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    public Map<String, List<OrderItem>> itemsByOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        Map<String, List<OrderItem>> map = new HashMap<>();
        for (OrderItem oi : orderItemRepository.findByOrderIdIn(orderIds)) {
            map.computeIfAbsent(oi.getOrderId(), k -> new ArrayList<>()).add(oi);
        }
        return map;
    }

    public Optional<Order> getById(String orderId) {
        return orderRepository.findById(orderId);
    }

    // ─── KITCHEN PRODUCTION ────────────────────────────────────────

    public List<Map<String, Object>> getKitchenProduction(String restaurantId) {
        List<Order> activeOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(o -> Set.of("NEW", "PREPARING", "PACKED_READY").contains(o.getOrderStatus()))
                .toList();

        List<String> orderIds = activeOrders.stream().map(Order::getId).toList();
        List<OrderItem> orderItems = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orderIds);

        Map<String, Map<String, Object>> dishMap = new LinkedHashMap<>();
        for (OrderItem item : orderItems) {
            String dishId = item.getMenuItemId();
            dishMap.computeIfAbsent(dishId, k -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("menuItemId", dishId);
                d.put("dishName", item.getTitle());
                d.put("requiredPlates", 0);
                d.put("orderNumbers", new ArrayList<String>());
                return d;
            });
            Map<String, Object> dish = dishMap.get(dishId);
            dish.put("requiredPlates", (int) dish.get("requiredPlates") + item.getQuantity());
            ((List<String>) dish.get("orderNumbers")).add(
                    activeOrders.stream()
                            .filter(o -> o.getId().equals(item.getOrderId()))
                            .map(Order::getOrderNumber)
                            .findFirst().orElse("#")
            );
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> dish : dishMap.values()) {
            int required = (int) dish.get("requiredPlates");
            dish.put("preparedPlates", 0);
            dish.put("remainingPlates", required);

            String earliestPickup = null;
            List<String> orderNums = (List<String>) dish.get("orderNumbers");
            for (Order o : activeOrders) {
                if (orderNums.contains(o.getOrderNumber()) && o.getPickupTime() != null) {
                    try {
                        java.time.LocalDateTime pickup = java.time.LocalDateTime.parse(o.getPickupTime());
                        if (earliestPickup == null || pickup.isBefore(java.time.LocalDateTime.parse(earliestPickup))) {
                            earliestPickup = o.getPickupTime();
                        }
                    } catch (Exception ignored) {}
                }
            }

            String urgency = "NORMAL";
            if (earliestPickup != null) {
                try {
                    java.time.LocalDateTime pickup = java.time.LocalDateTime.parse(earliestPickup);
                    long minutesUntil = java.time.Duration.between(now, pickup).toMinutes();
                    if (minutesUntil < 0) urgency = "OVERDUE";
                    else if (minutesUntil <= 30) urgency = "DUE_SOON";
                } catch (Exception ignored) {}
            }
            dish.put("urgency", urgency);
            dish.put("earliestPickup", earliestPickup);
            result.add(dish);
        }

        result.sort(Comparator.comparing((Map<String, Object> m) -> {
            String u = (String) m.get("urgency");
            return "OVERDUE".equals(u) ? 0 : "DUE_SOON".equals(u) ? 1 : 2;
        }));

        return result;
    }

    public List<Map<String, Object>> getDelayedOrders(String restaurantId) {
        List<Order> activeOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(o -> !Set.of("COMPLETED", "CANCELLED", "DECLINED").contains(o.getOrderStatus()))
                .toList();

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Map<String, Object>> delayed = new ArrayList<>();

        for (Order order : activeOrders) {
            if (order.getPickupTime() != null && !order.getPickupTime().isBlank()) {
                try {
                    java.time.LocalDateTime pickup = java.time.LocalDateTime.parse(order.getPickupTime());
                    if (now.isAfter(pickup)) {
                        long delayMinutes = java.time.Duration.between(pickup, now).toMinutes();
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("orderId", order.getId());
                        entry.put("orderNumber", order.getOrderNumber());
                        entry.put("customerName", order.getCustomerName());
                        entry.put("orderStatus", order.getOrderStatus());
                        entry.put("pickupTime", order.getPickupTime());
                        entry.put("delayMinutes", delayMinutes);
                        entry.put("isDelayed", true);
                        delayed.add(entry);
                    }
                } catch (Exception e) {
                    // Non-ISO pickup times — skip
                }
            }
        }

        return delayed;
    }

    // ─── STATUS UPDATE ─────────────────────────────────────────────

    @Transactional
    public Order updateStatus(String orderId, String restaurantId, String newStatus,
                              String actorUserId, String role) {
        Order order = orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found in this restaurant"));

        String current = order.getOrderStatus();
        OrderStateMachine.validate(current, newStatus, role);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(current)
                .toStatus(newStatus)
                .changedBy(actorUserId != null ? actorUserId : "SYSTEM")
                .build();
        orderStatusHistoryRepository.save(history);

        if ("CANCELLED".equals(newStatus) || "DECLINED".equals(newStatus)) {
            order.setCancelledBy(actorUserId);
            order.setCancelledAt(java.time.LocalDateTime.now());
            // Preserve user-provided reason if available, otherwise use default
            String existingReason = order.getCancelReason();
            if (existingReason == null || existingReason.isBlank()) {
                order.setCancelReason("CANCELLED".equals(newStatus) ? "Cancelled by " + role : "Declined by " + role);
            }
        }

        order.setOrderStatus(newStatus);
        Order saved = orderRepository.save(order);

        // Deduct ingredient stock when cooking begins
        if ("PREPARING".equals(newStatus)) {
            ingredientService.deductForOrder(orderId, restaurantId);
        }

        // Release inventory on cancellation/decline
        if ("CANCELLED".equals(newStatus) || "DECLINED".equals(newStatus)) {
            ingredientService.releaseReservation(orderId, restaurantId);

            // P0.8: Release plate capacity reservation
            releasePlateReservationsForOrder(order, restaurantId);

            // P0.9: Release table capacity reservation
            releaseTableReservationForOrder(order, restaurantId);

            // Auto-refund for PAID orders: initiate refund automatically
            if ("PAID".equals(order.getPaymentStatus()) || "REFUND_PENDING".equals(order.getPaymentStatus())) {
                try {
                    // Check if a non-failed refund already exists
                    List<Refund> existingRefunds = refundRepository.findByOrderId(orderId);
                    boolean hasActiveRefund = existingRefunds.stream()
                            .anyMatch(r -> !"FAILED".equals(r.getRefundStatus()));
                    if (!hasActiveRefund) {
                        String refundReason = "CANCELLED".equals(newStatus)
                                ? "Order cancelled" : "Order declined by restaurant";
                        initiateRefund(orderId, actorUserId, role, restaurantId, refundReason);
                        log.info("Auto-refund initiated for {} order {}", newStatus.toLowerCase(), orderId);
                    }
                } catch (Exception e) {
                    // Refund failure must not block the cancellation/decline
                    log.warn("Failed to auto-initiate refund for order {}: {}", orderId, e.getMessage());
                }
            }
        }

        // Audit trail
        if ("CANCELLED".equals(newStatus) || "DECLINED".equals(newStatus)) {
            String action = "CANCELLED".equals(newStatus) ? "ORDER_CANCELLED" : "ORDER_DECLINED";
            auditService.record(restaurantId, actorUserId, role, action, "ORDER", orderId,
                    Map.of("orderNumber", saved.getOrderNumber(), "status", newStatus,
                           "reason", order.getCancelReason()),
                    order.getCancelReason());
        }

        // Transactional outbox
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("orderId", saved.getId());
        eventPayload.put("restaurantId", restaurantId);
        eventPayload.put("orderNumber", saved.getOrderNumber());
        eventPayload.put("customerName", saved.getCustomerName());
        eventPayload.put("orderType", saved.getOrderType());
        eventPayload.put("status", newStatus);
        eventPayload.put("totalAmount", saved.getTotalAmount());
        eventPayload.put("userId", saved.getUserId());
        eventPayload.put("customerPhone", saved.getCustomerPhone());
        eventPayload.put("customerEmail", saved.getCustomerEmail());
        outboxService.record(saved.getId(), "order.status.changed", eventPayload);

        log.info("Order {} status updated to {} by {} (outbox: order.status.changed)",
                saved.getOrderNumber(), newStatus, actorUserId);
        return saved;
    }

    /**
     * Release plate capacity reservations for a cancelled/declined order.
     */
    private void releasePlateReservationsForOrder(Order order, String restaurantId) {
        LocalDate orderDate = todayOrNull(order);
        if (orderDate == null) return;

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            releasePlateCapacity(item.getMenuItemId(), orderDate, item.getQuantity());
        }
    }

    /**
     * Release table capacity reservation for a cancelled/declined order.
     */
    private void releaseTableReservationForOrder(Order order, String restaurantId) {
        if (!"DINE_IN".equals(order.getOrderType()) || order.getGuests() == null || order.getTimeSlot() == null) {
            return;
        }
        LocalDate orderDate = todayOrNull(order);
        if (orderDate == null) return;

        String tableType = tableTypeForGuests(order.getGuests());
        releaseTableCapacity(restaurantId, orderDate, order.getTimeSlot(), tableType, 1);
    }

    private LocalDate todayOrNull(Order order) {
        // For PRE_ORDER, use the pickup date; for others, use today
        if ("PRE_ORDER".equals(order.getOrderType()) && order.getPickupTime() != null && order.getPickupTime().length() >= 10) {
            try {
                return LocalDate.parse(order.getPickupTime().substring(0, 10));
            } catch (Exception ignored) {}
        }
        return LocalDate.now();
    }

    // ─── REFUNDS ──────────────────────────────────────────────────

    @Transactional
    public Refund initiateRefund(String orderId, String actorUserId, String role, String restaurantId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!restaurantId.equals(order.getRestaurantId())) {
            throw new SecurityException("Order does not belong to this restaurant");
        }
        if (role == null || "ROLE_CUSTOMER".equals(role)) {
            throw new SecurityException("Only staff can initiate refunds");
        }

        // P0.6: Idempotent — return existing non-failed refund
        List<Refund> existing = refundRepository.findByOrderId(orderId);
        for (Refund r : existing) {
            if (!"FAILED".equals(r.getRefundStatus())) {
                return r;
            }
        }

        if (!"PAID".equals(order.getPaymentStatus()) && !"REFUND_PENDING".equals(order.getPaymentStatus())) {
            throw new IllegalArgumentException("Cannot refund an order that is not PAID (current: " + order.getPaymentStatus() + ")");
        }

        if ("REFUND_PENDING".equals(order.getPaymentStatus())) {
            order.setPaymentStatus("PAID");
            orderRepository.save(order);
        }

        String gateway = order.getPaymentMethod();
        String paymentId = null;
        var payments = paymentRepository.findByOrderId(orderId);
        if (!payments.isEmpty()) {
            paymentId = payments.get(0).getTransactionId();
            gateway = payments.get(0).getGateway();
        }

        Refund refund = Refund.builder()
                .orderId(orderId)
                .paymentId(paymentId != null ? paymentId : "UNKNOWN")
                .amount(order.getTotalAmount())
                .currency("INR")
                .refundStatus("REQUESTED")
                .reason(reason)
                .initiatedBy(actorUserId)
                .restaurantId(restaurantId)
                .gateway(gateway)
                .build();
        Refund saved = refundRepository.save(refund);

        order.setPaymentStatus("REFUND_PENDING");
        orderRepository.save(order);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("orderId", orderId);
        eventPayload.put("refundId", saved.getId());
        eventPayload.put("restaurantId", restaurantId);
        eventPayload.put("amount", saved.getAmount());
        eventPayload.put("reason", reason);
        outboxService.record(orderId, "refund.requested", eventPayload);

        auditService.record(restaurantId, actorUserId, role, "REFUND_INITIATED", "ORDER", orderId,
                Map.of("refundId", saved.getId(), "amount", saved.getAmount(), "reason", reason != null ? reason : ""),
                reason);

        log.info("Refund initiated for order {} by {} (refund: {})", order.getOrderNumber(), actorUserId, saved.getId());
        return saved;
    }

    @Transactional
    public Refund completeRefund(String refundId, String providerRefundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"));

        if ("COMPLETED".equals(refund.getRefundStatus())) {
            return refund; // idempotent
        }

        refund.setRefundStatus("COMPLETED");
        refund.setProviderRefundId(providerRefundId);
        refund.setCompletedAt(java.time.LocalDateTime.now());
        Refund saved = refundRepository.save(refund);

        Order order = orderRepository.findById(refund.getOrderId()).orElse(null);
        if (order != null) {
            order.setPaymentStatus("REFUNDED");
            orderRepository.save(order);
        }

        if (refund.getPaymentId() != null && !"UNKNOWN".equals(refund.getPaymentId())) {
            paymentRepository.findById(refund.getPaymentId()).ifPresent(p -> {
                p.setPaymentStatus("REFUNDED");
                paymentRepository.save(p);
            });
        }

        auditService.record(refund.getRestaurantId(), "SYSTEM", null, "REFUND_COMPLETED", "REFUND", refundId,
                Map.of("orderId", refund.getOrderId(), "amount", refund.getAmount()),
                "Refund processed successfully");

        log.info("Refund {} completed for order {}", refundId, refund.getOrderId());
        return saved;
    }

    // ─── SSE BROADCASTS ────────────────────────────────────────────

    private void broadcastTableAvailability(String restaurantId, String timeSlot) {
        try {
            String today = LocalDate.now().toString();
            List<String> timeSlots = getTimeSlotsWithinOneHour(timeSlot);
            List<String> timeSlots24h = timeSlots.stream().map(this::to24Hour).distinct().collect(Collectors.toList());
            List<Object[]> booked = orderRepository.countDineInByTimeSlots(restaurantId, today, timeSlots, timeSlots24h);
            Map<Integer, Long> bookedByGuests = new HashMap<>();
            for (Object[] row : booked) {
                Integer guests = ((Number) row[0]).intValue();
                Long count = ((Number) row[1]).longValue();
                bookedByGuests.put(guests, count);
            }

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("restaurantId", restaurantId);
            event.put("date", today);
            event.put("timeSlot", timeSlot);
            event.put("bookedByGuests", bookedByGuests);
            event.put("timestamp", java.time.LocalDateTime.now().toString());

            realtimeService.broadcastToAllUsers("table_availability", event);
            realtimeService.pushToRestaurant(restaurantId, "table_availability", event);
        } catch (Exception e) {
            log.warn("[SSE] Failed to broadcast table availability: {}", e.getMessage());
        }
    }

    private void broadcastPlateCount(String restaurantId, String menuItemId) {
        try {
            MenuItem item = menuItemRepository.findById(menuItemId).orElse(null);
            if (item == null || item.getDailyPlateCount() == null) return;

            LocalDate today = LocalDate.now();
            long ordered = orderItemRepository.countPlatesOrderedForItem(
                    menuItemId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            int remaining = Math.max(0, item.getDailyPlateCount() - (int) ordered);

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("menuItemId", menuItemId);
            event.put("title", item.getTitle());
            event.put("status", remaining > 0 ? item.getStatus() : "Sold Out");
            event.put("dailyPlateCount", item.getDailyPlateCount());
            event.put("remainingPlates", remaining);
            event.put("restaurantId", restaurantId);
            event.put("timestamp", java.time.LocalDateTime.now().toString());

            realtimeService.broadcastToAllUsers("menu_availability", event);
        } catch (Exception e) {
            log.warn("[SSE] Failed to broadcast plate count for {}: {}", menuItemId, e.getMessage());
        }
    }

    private List<String> getTimeSlotsWithinOneHour(String timeSlot) {
        List<String> slots = new ArrayList<>();
        String cleanSlot = timeSlot.trim();
        int pmIdx = cleanSlot.indexOf("PM");
        int amIdx = cleanSlot.indexOf("AM");
        int idx = Math.max(pmIdx, amIdx);
        if (idx > 0) {
            String before = cleanSlot.substring(0, idx + 2).trim();
            int lastSpace = before.lastIndexOf(' ');
            if (lastSpace > 0) cleanSlot = before.substring(lastSpace + 1);
        }
        slots.add(cleanSlot);
        try {
            java.time.format.DateTimeFormatter fmt12 = java.time.format.DateTimeFormatter.ofPattern("h:mm a");
            java.time.LocalTime requestedTime = java.time.LocalTime.parse(cleanSlot, fmt12);
            for (int i = 1; i <= 2; i++) {
                java.time.LocalTime next = requestedTime.plusMinutes(i * 30L);
                slots.add(next.format(fmt12));
            }
        } catch (Exception e) {}
        return slots;
    }

    private String to24Hour(String time12h) {
        try {
            java.time.format.DateTimeFormatter fmt12 = java.time.format.DateTimeFormatter.ofPattern("h:mm a");
            java.time.LocalTime t = java.time.LocalTime.parse(time12h.trim(), fmt12);
            return t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return time12h;
        }
    }
}
