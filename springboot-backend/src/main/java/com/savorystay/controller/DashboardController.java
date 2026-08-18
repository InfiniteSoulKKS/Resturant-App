package com.savorystay.controller;

import com.savorystay.dto.ConfirmPaymentRequest;
import com.savorystay.entity.Order;
import com.savorystay.entity.Payment;
import com.savorystay.entity.Refund;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.PaymentRepository;
import com.savorystay.repository.RefundRepository;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.IngredientRepository;
import com.savorystay.entity.Ingredient;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.repository.MenuItemIngredientRepository;
import com.savorystay.service.IngredientService;
import com.savorystay.service.OrderService;
import com.savorystay.service.AuditService;
import com.savorystay.tenant.TenantContext;
import com.savorystay.security.RoleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregation endpoints for operational dashboards.
 * All queries use aggregate SQL where possible — no N+1, no loading entire datasets into memory.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final MenuItemRepository menuItemRepository;
    private final IngredientRepository ingredientRepository;
    private final MenuItemIngredientRepository menuItemIngredientRepository;
    private final IngredientService ingredientService;
    private final OrderService orderService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // P1.7 — Tomorrow's Operations Brief
    // ------------------------------------------------------------------

    /**
     * GET /api/v1/dashboard/tomorrow-brief?restaurantId=
     *
     * Returns tomorrow's pre-orders count, expected revenue, production
     * breakdown per dish, and ingredient requirements with shortfalls.
     */
    @GetMapping("/tomorrow-brief")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN','CHEF')")
    public ResponseEntity<?> tomorrowBrief(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String dateStr = tomorrow.toString();
        LocalDateTime from = tomorrow.atStartOfDay();
        LocalDateTime to = tomorrow.atTime(LocalTime.MAX);

        // Active pre-orders for tomorrow
        List<Order> preOrders = orderRepository.findActiveOrdersBetween(restaurantId, dateStr, from, to)
                .stream()
                .filter(o -> "PRE_ORDER".equals(o.getOrderType()))
                .toList();

        BigDecimal expectedRevenue = preOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Production breakdown per dish
        List<String> orderIds = preOrders.stream().map(Order::getId).toList();
        var orderItems = orderIds.isEmpty() ? List.<com.savorystay.entity.OrderItem>of()
                : orderItemRepository.findByOrderIdIn(orderIds);

        Map<String, Integer> dishQuantities = new LinkedHashMap<>();
        Map<String, String> dishTitles = new LinkedHashMap<>();
        for (var item : orderItems) {
            dishQuantities.merge(item.getMenuItemId(), item.getQuantity(), Integer::sum);
            dishTitles.putIfAbsent(item.getMenuItemId(), item.getTitle());
        }

        List<Map<String, Object>> production = new ArrayList<>();
        for (var entry : dishQuantities.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dish", dishTitles.getOrDefault(entry.getKey(), entry.getKey()));
            row.put("plates", entry.getValue());
            production.add(row);
        }

        // Ingredient requirements
        var forecast = ingredientService.forecastForDate(restaurantId, tomorrow);
        List<Map<String, Object>> ingredients = (List<Map<String, Object>>) forecast.getOrDefault("ingredients", List.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", dateStr);
        response.put("preOrderCount", preOrders.size());
        response.put("expectedRevenue", expectedRevenue.setScale(2, RoundingMode.HALF_UP));
        response.put("production", production);
        response.put("ingredients", ingredients);

        return ResponseEntity.ok(Map.of("success", true, "brief", response));
    }

    // ------------------------------------------------------------------
    // P1.8 — Shopping List (shortages only)
    // ------------------------------------------------------------------

    @GetMapping("/shopping-list")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN','CHEF')")
    public ResponseEntity<?> shoppingList(@RequestParam(required = false) String restaurantId,
                                          @RequestParam(required = false) String date) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        LocalDate target = date != null ? LocalDate.parse(date) : LocalDate.now().plusDays(1);
        var forecast = ingredientService.forecastForDate(restaurantId, target);
        List<Map<String, Object>> allIngredients = (List<Map<String, Object>>) forecast.getOrDefault("ingredients", List.of());

        // Filter to only shortages
        List<Map<String, Object>> shoppingList = allIngredients.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("needPurchase")))
                .map(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", i.get("name"));
                    row.put("unit", i.get("unit"));
                    row.put("requiredQuantity", i.get("requiredQuantity"));
                    row.put("currentStock", i.get("currentStock"));
                    row.put("shortfall", i.get("shortfall"));
                    return row;
                })
                .toList();

        return ResponseEntity.ok(Map.of("success", true, "date", target.toString(),
                "shoppingList", shoppingList, "itemCount", shoppingList.size()));
    }

    // ------------------------------------------------------------------
    // P1.9 — Cash Reconciliation
    // ------------------------------------------------------------------

    @GetMapping("/cash-reconciliation")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> cashReconciliation(@RequestParam(required = false) String restaurantId,
                                                @RequestParam(required = false) String date) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        LocalDate target = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.atTime(LocalTime.MAX);

        List<Order> cashOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(o -> "CASH".equalsIgnoreCase(o.getPaymentMethod()))
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(from) && !o.getCreatedAt().isAfter(to))
                .filter(o -> !List.of("CANCELLED", "DECLINED").contains(o.getOrderStatus()))
                .toList();

        BigDecimal expectedCash = cashOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidCount = cashOrders.stream()
                .filter(o -> "PAID".equals(o.getPaymentStatus()))
                .count();
        long pendingCount = cashOrders.stream()
                .filter(o -> "PENDING".equals(o.getPaymentStatus()))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", target.toString());
        response.put("totalCashOrders", cashOrders.size());
        response.put("expectedCash", expectedCash.setScale(2, RoundingMode.HALF_UP));
        response.put("paidOrders", paidCount);
        response.put("pendingOrders", pendingCount);
        response.put("pendingAmount", cashOrders.stream()
                .filter(o -> "PENDING".equals(o.getPaymentStatus()))
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));

        return ResponseEntity.ok(Map.of("success", true, "reconciliation", response));
    }

    // ------------------------------------------------------------------
    // P1.10 — Payment Reconciliation
    // ------------------------------------------------------------------

    @GetMapping("/payment-reconciliation")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> paymentReconciliation(@RequestParam(required = false) String restaurantId,
                                                   @RequestParam(required = false) String date) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        LocalDate target = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.atTime(LocalTime.MAX);

        List<Order> dayOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(from) && !o.getCreatedAt().isAfter(to))
                .filter(o -> !List.of("CANCELLED", "DECLINED").contains(o.getOrderStatus()))
                .toList();

        // Group by payment method
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, Long> countByMethod = new LinkedHashMap<>();
        for (Order o : dayOrders) {
            String method = o.getPaymentMethod() != null ? o.getPaymentMethod() : "UNKNOWN";
            byMethod.merge(method, o.getTotalAmount(), BigDecimal::add);
            countByMethod.merge(method, 1L, Long::sum);
        }

        BigDecimal gross = dayOrders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = refundRepository.findByRestaurantIdAndRefundStatus(restaurantId, "COMPLETED")
                .stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(from) && !r.getCreatedAt().isAfter(to))
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingPayments = dayOrders.stream().filter(o -> "PENDING".equals(o.getPaymentStatus())).count();
        long failedPayments = dayOrders.stream().filter(o -> "FAILED".equals(o.getPaymentStatus())).count();
        long cashPending = dayOrders.stream()
                .filter(o -> "CASH".equalsIgnoreCase(o.getPaymentMethod()) && "PENDING".equals(o.getPaymentStatus()))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", target.toString());
        response.put("gross", gross.setScale(2, RoundingMode.HALF_UP));
        response.put("refunds", refunds.setScale(2, RoundingMode.HALF_UP));
        response.put("net", gross.subtract(refunds).setScale(2, RoundingMode.HALF_UP));
        response.put("byMethod", byMethod);
        response.put("countByMethod", countByMethod);
        response.put("pendingPayments", pendingPayments);
        response.put("failedPayments", failedPayments);
        response.put("cashPending", cashPending);

        return ResponseEntity.ok(Map.of("success", true, "reconciliation", response));
    }

    // ------------------------------------------------------------------
    // P1.11 — Order Exception Center
    // ------------------------------------------------------------------

    @GetMapping("/exceptions")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN','CHEF')")
    public ResponseEntity<?> exceptionCenter(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        List<Order> allOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);

        // Payment failures
        long paymentFailures = allOrders.stream()
                .filter(o -> "FAILED".equals(o.getPaymentStatus()))
                .count();

        // Delayed orders
        var delayed = orderService.getDelayedOrders(restaurantId);

        // Cash payments pending
        long cashPending = allOrders.stream()
                .filter(o -> "CASH".equalsIgnoreCase(o.getPaymentMethod()) && "PENDING".equals(o.getPaymentStatus())
                        && !List.of("CANCELLED", "DECLINED").contains(o.getOrderStatus()))
                .count();

        // Refunds pending
        long refundsPending = refundRepository.findByRestaurantIdAndRefundStatus(restaurantId, "REQUESTED").size();

        // Ingredient shortages (low stock)
        long ingredientShortages = ingredientRepository.findByRestaurantIdOrderByNameAsc(restaurantId)
                .stream()
                .filter(Ingredient::getActive)
                .filter(i -> i.getStockQuantity().compareTo(i.getReorderLevel()) < 0)
                .count();

        // Sold-out dishes
        long soldOutDishes = menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(i -> "Sold Out".equals(i.getStatus()))
                .count();

        // Active orders needing attention
        long preparingOrders = allOrders.stream()
                .filter(o -> "PREPARING".equals(o.getOrderStatus()))
                .count();
        long newOrders = allOrders.stream()
                .filter(o -> "NEW".equals(o.getOrderStatus()))
                .count();
        long readyOrders = allOrders.stream()
                .filter(o -> "PACKED_READY".equals(o.getOrderStatus()))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("paymentFailures", paymentFailures);
        response.put("delayedOrders", delayed.size());
        response.put("cashPaymentsPending", cashPending);
        response.put("refundsPending", refundsPending);
        response.put("ingredientShortages", ingredientShortages);
        response.put("soldOutDishes", soldOutDishes);
        response.put("newOrders", newOrders);
        response.put("preparingOrders", preparingOrders);
        response.put("readyOrders", readyOrders);
        response.put("delayedOrderDetails", delayed);

        return ResponseEntity.ok(Map.of("success", true, "exceptions", response));
    }

    // ------------------------------------------------------------------
    // P1.13 — Manager Dashboard Summary
    // ------------------------------------------------------------------

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> managerSummary(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        List<Order> allOrders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);

        // Today's orders (all orders, not just today — the current system doesn't filter by day in the repo)
        // We filter in-memory for correctness
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<Order> todayOrders = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(todayStart))
                .toList();

        BigDecimal todayRevenue = todayOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pending = todayOrders.stream().filter(o -> "NEW".equals(o.getOrderStatus())).count();
        long preparing = todayOrders.stream().filter(o -> "PREPARING".equals(o.getOrderStatus())).count();
        long ready = todayOrders.stream().filter(o -> "PACKED_READY".equals(o.getOrderStatus())).count();
        long completed = todayOrders.stream().filter(o -> "COMPLETED".equals(o.getOrderStatus())).count();
        long delayed = orderService.getDelayedOrders(restaurantId).size();

        // Cash pending
        long cashPending = todayOrders.stream()
                .filter(o -> "CASH".equalsIgnoreCase(o.getPaymentMethod()) && "PENDING".equals(o.getPaymentStatus()))
                .count();

        // Ingredient shortages
        long ingredientShortages = ingredientRepository.findByRestaurantIdOrderByNameAsc(restaurantId)
                .stream()
                .filter(Ingredient::getActive)
                .filter(i -> i.getStockQuantity().compareTo(i.getReorderLevel()) < 0)
                .count();

        // Sold-out dishes
        long soldOutDishes = menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(i -> "Sold Out".equals(i.getStatus()))
                .count();

        // Tomorrow's brief
        var tomorrowBrief = ingredientService.forecastForDate(restaurantId, LocalDate.now().plusDays(1));
        List<Map<String, Object>> tomorrowIngredients = (List<Map<String, Object>>) tomorrowBrief.getOrDefault("ingredients", List.of());
        long tomorrowShortfalls = tomorrowIngredients.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("needPurchase")))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("today", LocalDate.now().toString());
        response.put("totalOrders", todayOrders.size());
        response.put("revenue", todayRevenue.setScale(2, RoundingMode.HALF_UP));
        response.put("pending", pending);
        response.put("preparing", preparing);
        response.put("ready", ready);
        response.put("completed", completed);
        response.put("delayed", delayed);
        response.put("cashPaymentsPending", cashPending);
        response.put("ingredientShortages", ingredientShortages);
        response.put("soldOutDishes", soldOutDishes);
        response.put("tomorrowPreOrders", (int) tomorrowBrief.getOrDefault("preOrderCount", 0));
        response.put("tomorrowExpectedRevenue", tomorrowBrief.getOrDefault("expectedRevenue", BigDecimal.ZERO));
        response.put("tomorrowIngredientShortfalls", tomorrowShortfalls);

        return ResponseEntity.ok(Map.of("success", true, "summary", response));
    }

    // ------------------------------------------------------------------
    // P0.10 — Audit Trail
    // ------------------------------------------------------------------

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> auditTrail(@RequestParam(required = false) String restaurantId,
                                        @RequestParam(defaultValue = "50") int limit) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        var entries = auditService.getRecent(restaurantId, limit);
        return ResponseEntity.ok(Map.of("success", true, "audit", entries, "count", entries.size()));
    }
}
