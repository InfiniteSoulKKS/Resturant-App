package com.savorystay.service;

import com.savorystay.common.OrderStateMachine;
import com.savorystay.dto.OrderItemRequest;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;
import com.savorystay.entity.OrderStatusHistory;
import com.savorystay.entity.Payment;
import com.savorystay.entity.Restaurant;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.OrderStatusHistoryRepository;
import com.savorystay.repository.PaymentRepository;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.security.RoleUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.savorystay.entity.Refund;
import com.savorystay.repository.RefundRepository;

import java.util.UUID;

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

    private static final List<String> FLOW = List.of("NEW", "PREPARING", "PACKED_READY", "COMPLETED");

    /** Transitions a chef-only account is allowed to perform (cook + pack). */
    private static final Set<String> CHEF_TRANSITIONS = Set.of(
            "NEW->PREPARING",
            "PREPARING->PACKED_READY"
    );

    /**
     * Customer places an order against a restaurant's menu.
     * Payment status is ALWAYS server-set to PENDING here — clients can never
     * mark their own orders paid. It can only move to PAID via confirmPayment().
     */
    @Transactional
    public Order placeOrder(String userId, String restaurantId, String customerName,
                            String customerPhone, String customerEmail, String orderType,
                            Integer tableNumber, Integer guests, String timeSlot, String pickupTime,
                            List<OrderItemRequest> items, String paymentMethod) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // A suspended restaurant must not accept orders — neither now nor via
        // pre-orders. The super admin suspends a restaurant to take it offline;
        // customers hitting the API directly (or the menu cached in their
        // browser) must get a clear business error instead of an order that
        // will never be fulfilled.
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        if (!"ACTIVE".equals(restaurant.getStatus())) {
            throw new IllegalArgumentException(
                    "This restaurant is currently offline (" + restaurant.getStatus()
                            + ") — orders cannot be placed right now.");
        }

        String effectiveOrderType = orderType != null ? orderType.toUpperCase() : "PICKUP";

        // PRE_ORDER: enforce every business rule (horizon, cutoff, closure,
        // dish availability) BEFORE pricing so customers get a clear error
        // instead of an order that can never be fulfilled.
        if ("PRE_ORDER".equals(effectiveOrderType)) {
            validatePreOrder(restaurantId, pickupTime, items);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest line : items) {
            String menuItemId = line.menuItemId();
            int qty = line.quantity() != null ? line.quantity() : 1;
            if (qty <= 0) {
                throw new IllegalArgumentException("Quantity must be a positive number");
            }
            MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                    .orElseThrow(() -> new IllegalArgumentException("Menu item not found: " + menuItemId));
            if (!"Available".equals(menuItem.getStatus())) {
                throw new IllegalArgumentException("Item is sold out: " + menuItem.getTitle());
            }

            // Use the effective price (latest price_rule <= now), falling back to base price
            BigDecimal effectivePrice = menuService.getEffectivePrice(menuItem.getId(), menuItem.getPrice());
            BigDecimal lineTotal = effectivePrice.multiply(BigDecimal.valueOf(qty));
            total = total.add(lineTotal);

            orderItems.add(OrderItem.builder()
                    .orderId(null) // set after order save
                    .menuItemId(menuItemId)
                    .title(menuItem.getTitle())
                    .quantity(qty)
                    .unitPrice(effectivePrice)
                    .notes(line.notes())
                    .build());
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
                .paymentStatus("PENDING") // server-authoritative; never trust the client here
                .paymentMethod(paymentMethod != null ? paymentMethod : "MOCK")
                .orderStatus("NEW")
                .build();
        Order saved = orderRepository.save(order);

        orderItems.forEach(oi -> oi.setOrderId(saved.getId()));
        orderItemRepository.saveAll(orderItems);

        // Transactional outbox: record order.created in the SAME transaction as the order.
        // OutboxPoller dispatches this to NotificationService / SSE asynchronously.
        Map<String, Object> eventPayload = new HashMap<>();
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

        // Auto-join: if the restaurant has autoJoinCustomers enabled and this
        // customer is not yet a member, add them automatically so they can
        // use the restaurant picker on subsequent logins.
        if (userId != null && Boolean.TRUE.equals(restaurant.getAutoJoinCustomers())) {
            try {
                if (!customerRestaurantService.isMember(userId, restaurantId)) {
                    customerRestaurantService.join(userId, restaurantId, null);
                    log.info("Auto-joined customer {} to restaurant {} on first order", userId, restaurantId);
                }
            } catch (Exception e) {
                // Non-critical — log and continue; the order is already placed.
                log.warn("Failed to auto-join customer {} to restaurant {}: {}", userId, restaurantId, e.getMessage());
            }
        }

        return saved;
    }

    /**
     * Validates that a PRE_ORDER can be fulfilled: the pickup date must be
     * within the ordering horizon, the cutoff must not have passed, the
     * restaurant must be open (pickup within operating hours), and every dish
     * must be available on that date. All decisions use the business timezone
     * via {@link BusinessClock}.
     */
    private void validatePreOrder(String restaurantId, String pickupTime,
                                  List<OrderItemRequest> items) {
        // Pre-orders carry an ISO pickup datetime, e.g. "2026-08-14T19:30:00".
        // Missing/invalid pickupTime is a client error — every pre-order must
        // state the fulfillment date.
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
        } catch (Exception ignored) {
            // time is optional for the rules below (open-hours check only)
        }

        List<String> menuItemIds = items.stream().map(OrderItemRequest::menuItemId).toList();
        availabilityService.validatePreOrder(restaurantId, date, time, menuItemIds);
    }

    /**
     * Server-authoritative payment confirmation.
     * Marks an order PAID only after the server verifies the caller is the order
     * owner (or staff of the order's restaurant) and, when provided, that the paid
     * amount matches the order total. The Payment row is recorded in the same
     * transaction for audit.
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

        if ("PAID".equals(order.getPaymentStatus())) {
            return order; // idempotent — already confirmed
        }

        // Idempotency: if a payment already exists for this order, return it
        if (!paymentRepository.findByOrderId(orderId).isEmpty()) {
            order.setPaymentStatus("PAID");
            return orderRepository.save(order);
        }

        // CASH / Pay-on-Pickup orders must not be confirmed online — the customer
        // pays at the counter. The restaurant staff should mark the order as PAID
        // through a separate "mark-paid" flow when cash is collected.
        if ("CASH".equalsIgnoreCase(gateway) || "CASH".equalsIgnoreCase(order.getPaymentMethod())) {
            throw new IllegalArgumentException("CASH orders cannot be confirmed online — customer pays at pickup");
        }

        // Amount is required — the server must always verify the payment value
        // against the order total. Compare at 2-decimal precision so JSON
        // floating-point totals (e.g. 123.44999…) still match the exact total.
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

        // Transactional outbox: payment.confirmed in the SAME transaction so the
        // customer always receives their receipt (Gmail) via the Kafka pipeline.
        Map<String, Object> paymentPayload = new HashMap<>();
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

    public List<Order> ordersForCustomer(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Order> ordersForRestaurant(String restaurantId) {
        return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    /**
     * Add or update a kitchen note on an order item.
     * Notes are visible in the kitchen production view.
     */
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

    /** Batch lookup of line items for many orders (avoids N+1 on dashboards). */
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

    /**
     * Kitchen production view — aggregates active orders by dish for the current day.
     * Shows required plates, urgency based on pickup time, and which orders need each dish.
     * Orders in NEW/PREPARING/PACKED_READY status count toward production.
     */
    public List<Map<String, Object>> getKitchenProduction(String restaurantId) {
        List<Order> activeOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(o -> Set.of("NEW", "PREPARING", "PACKED_READY").contains(o.getOrderStatus()))
                .toList();

        List<String> orderIds = activeOrders.stream().map(Order::getId).toList();
        List<OrderItem> orderItems = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orderIds);

        // Aggregate by dish
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

        // Calculate urgency for each dish based on earliest pickup time of its orders
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> dish : dishMap.values()) {
            int required = (int) dish.get("requiredPlates");
            dish.put("preparedPlates", 0);
            dish.put("remainingPlates", required);

            // Calculate urgency from earliest pickup time among this dish's orders
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
                    if (minutesUntil < 0) {
                        urgency = "OVERDUE";
                    } else if (minutesUntil <= 30) {
                        urgency = "DUE_SOON";
                    }
                } catch (Exception ignored) {}
            }
            dish.put("urgency", urgency);
            dish.put("earliestPickup", earliestPickup);
            result.add(dish);
        }

        // Sort by urgency (OVERDUE first, then DUE_SOON, then NORMAL)
        result.sort(Comparator.comparing((Map<String, Object> m) -> {
            String u = (String) m.get("urgency");
            return "OVERDUE".equals(u) ? 0 : "DUE_SOON".equals(u) ? 1 : 2;
        }));

        return result;
    }

    /**
     * Detect delayed orders — orders where current time > promised pickup time
     * and order is not yet completed.
     */
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
                    // Try parsing ISO datetime format (pre-orders)
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
                    // Non-ISO pickup times (e.g. "30 Mins (Ready by 07:45 PM)") — skip
                }
            }
        }

        return delayed;
    }

    /**
     * Manager / Chef / Admin advances the order workflow.
     * When an order becomes PACKED_READY the customer gets a real-time
     * "order ready" notification (App push + SMS/WhatsApp/Email).
     *
     * Role rules:
     *  - Chef-only accounts may cook (NEW -> PREPARING) and pack (PREPARING ->
     *    PACKED_READY). They cannot decline, complete, or hand over.
     *  - Managers / admins / super admins (and dual-role Manager+Chef users)
     *    may perform the full flow, including decline and handover.
     */
    @Transactional
    public Order updateStatus(String orderId, String restaurantId, String newStatus,
                              String actorUserId, String role) {
        Order order = orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found in this restaurant"));

        String current = order.getOrderStatus();
        OrderStateMachine.validate(current, newStatus, role);

        // Write to append-only audit trail BEFORE mutating the order
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(current)
                .toStatus(newStatus)
                .changedBy(actorUserId != null ? actorUserId : "SYSTEM")
                .build();
        orderStatusHistoryRepository.save(history);

        // Record cancellation/decline metadata if applicable
        if ("CANCELLED".equals(newStatus) || "DECLINED".equals(newStatus)) {
            order.setCancelledBy(actorUserId);
            order.setCancelledAt(java.time.LocalDateTime.now());
            if ("CANCELLED".equals(newStatus)) {
                order.setCancelReason("Cancelled by " + role);
            } else {
                order.setCancelReason("Declined by " + role);
            }
        }

        order.setOrderStatus(newStatus);
        Order saved = orderRepository.save(order);

        // Deduct ingredient stock when cooking begins
        if ("PREPARING".equals(newStatus)) {
            ingredientService.deductForOrder(orderId, restaurantId);
        }

        // Release inventory reservation on cancellation/decline
        if ("CANCELLED".equals(newStatus) || "DECLINED".equals(newStatus)) {
            ingredientService.releaseReservation(orderId, restaurantId);
        }

        // Audit trail for cancellation/decline
        if ("CANCELLED".equals(newStatus) || "DECLINED".equals(newStatus)) {
            String action = "CANCELLED".equals(newStatus) ? "ORDER_CANCELLED" : "ORDER_DECLINED";
            auditService.record(restaurantId, actorUserId, role, action, "ORDER", orderId,
                    Map.of("orderNumber", saved.getOrderNumber(), "status", newStatus,
                           "reason", order.getCancelReason()),
                    order.getCancelReason());
        }

        // Transactional outbox: record order.status.changed in the SAME transaction.
        // OutboxPoller dispatches customer + staff notifications asynchronously.
        Map<String, Object> eventPayload = new HashMap<>();
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
     * Initiate a refund for a paid order. Creates a Refund record in REQUESTED state.
     * The actual provider refund should be processed asynchronously (webhook or polling).
     * Full refunds only for now — structured so PARTIAL_REFUND can be added later.
     */
    @Transactional
    public Refund initiateRefund(String orderId, String actorUserId, String role, String restaurantId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Tenant isolation
        if (!restaurantId.equals(order.getRestaurantId())) {
            throw new SecurityException("Order does not belong to this restaurant");
        }

        // Only staff can initiate refunds
        if (role == null || "ROLE_CUSTOMER".equals(role)) {
            throw new SecurityException("Only staff can initiate refunds");
        }

        // Idempotent: if any refund already exists, return it
        List<Refund> existing = refundRepository.findByOrderId(orderId);
        for (Refund r : existing) {
            if (!"FAILED".equals(r.getRefundStatus())) {
                return r; // already refunded, in progress, or requested
            }
        }

        // Must be paid (or REFUND_PENDING from a previous REQUESTED refund that was lost)
        if (!"PAID".equals(order.getPaymentStatus()) && !"REFUND_PENDING".equals(order.getPaymentStatus())) {
            throw new IllegalArgumentException("Cannot refund an order that is not PAID (current: " + order.getPaymentStatus() + ")");
        }

        // If already REFUND_PENDING but no refund record found (data inconsistency), reset to PAID
        if ("REFUND_PENDING".equals(order.getPaymentStatus())) {
            order.setPaymentStatus("PAID");
            orderRepository.save(order);
        }

        // Find the payment record to get gateway info
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

        // Update order payment status to indicate refund is pending
        order.setPaymentStatus("REFUND_PENDING");
        orderRepository.save(order);

        // Outbox event
        Map<String, Object> eventPayload = new HashMap<>();
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

    /**
     * Mark a refund as completed (called by webhook handler or async processor).
     */
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

        // Update order payment status
        Order order = orderRepository.findById(refund.getOrderId()).orElse(null);
        if (order != null) {
            order.setPaymentStatus("REFUNDED");
            orderRepository.save(order);
        }

        // Update payment status
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

    // Transition validation is now handled by OrderStateMachine.validate()
}
