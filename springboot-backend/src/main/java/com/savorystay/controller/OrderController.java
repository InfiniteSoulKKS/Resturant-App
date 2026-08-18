package com.savorystay.controller;

import com.savorystay.dto.ConfirmPaymentRequest;
import com.savorystay.dto.OrderItemResponse;
import com.savorystay.dto.OrderResponse;
import com.savorystay.dto.PlaceOrderRequest;
import com.savorystay.dto.UpdateOrderStatusRequest;
import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;
import com.savorystay.service.AuditService;
import com.savorystay.service.OrderService;
import com.savorystay.security.RoleUtils;
import com.savorystay.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;
    private final AuditService auditService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody PlaceOrderRequest req) {
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();

            // Customer-only ordering. SecurityConfig already fails closed at the
            // filter (POST /api/v1/orders requires ROLE_CUSTOMER); this guard
            // keeps a clear message in case that rule is ever loosened.
            if (role != null && !RoleUtils.hasRole(role, RoleUtils.CUSTOMER)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message",
                        "Only customer accounts can place orders. Staff accounts manage the kitchen instead."));
            }

            // Tenant isolation: staff are locked to their own restaurant; only
            // customers and super admins may choose an arbitrary restaurantId.
            String restaurantId;
            if (role != null && !"ROLE_CUSTOMER".equals(role) && !"ROLE_SUPER_ADMIN".equals(role)) {
                restaurantId = TenantContext.getRestaurantId();
            } else {
                restaurantId = req.restaurantId();
            }
            if (restaurantId == null || restaurantId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "restaurantId is required"));
            }
            String customerName = req.customerName() != null ? req.customerName() : "Guest";
            String orderType = req.orderType() != null ? req.orderType() : "PICKUP";
            String paymentMethod = req.paymentMethod() != null ? req.paymentMethod() : "MOCK";
            // NOTE: paymentStatus is intentionally NOT read from the request body — the
            // server always creates orders as PENDING. See OrderService.confirmPayment().

            Order order = orderService.placeOrder(
                    userId, restaurantId, customerName, req.customerPhone(), req.customerEmail(),
                    orderType, req.tableNumber(), req.guests(), req.timeSlot(), req.pickupTime(),
                    req.items(), paymentMethod);

            return ResponseEntity.ok(Map.of("success", true, "order", OrderResponse.from(order, orderService.itemsFor(order.getId())),
                    "message", "Order placed successfully"));
        } catch (Exception e) {
            log.error("Error placing order: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Server-authoritative payment confirmation.
     * Body: { "amount": 840, "gateway": "UPI" } (amount verified against the order total)
     * Only the order owner or the restaurant's staff may confirm payment.
     */
    @PostMapping("/{orderId}/payment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> confirmPayment(@PathVariable String orderId, @Valid @RequestBody ConfirmPaymentRequest req) {
        try {
            Order updated = orderService.confirmPayment(
                    orderId, TenantContext.getUserId(), TenantContext.getRole(),
                    TenantContext.getRestaurantId(), req.amount(), req.gateway());

            return ResponseEntity.ok(Map.of("success", true, "order", OrderResponse.from(updated, orderService.itemsFor(orderId)),
                    "message", "Payment confirmed"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error confirming payment for order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> myOrders() {
        String userId = TenantContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }
        List<Order> orders = orderService.ordersForCustomer(userId);
        Map<String, List<OrderItem>> itemsByOrder = orderService.itemsByOrderIds(orders.stream().map(Order::getId).toList());
        List<OrderResponse> dtos = orders.stream()
                .map(o -> OrderResponse.from(o, itemsByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "orders", dtos));
    }

    /**
     * Restaurant order queue — STAFF ONLY. A plain customer account must never be
     * able to list another restaurant's orders (customer names/phones/totals), so
     * this endpoint requires a staff role rather than mere authentication. Staff
     * are locked to their own restaurant; super admins may pass ?restaurantId=.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> restaurantOrders(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<Order> orders = orderService.ordersForRestaurant(restaurantId);
        Map<String, List<OrderItem>> itemsByOrder = orderService.itemsByOrderIds(orders.stream().map(Order::getId).toList());
        List<OrderResponse> dtos = orders.stream()
                .map(o -> OrderResponse.from(o, itemsByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "orders", dtos));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> orderDetail(@PathVariable String orderId,
                                         @RequestParam(required = false) String restaurantId) {
        String userId = TenantContext.getUserId();
        String role = TenantContext.getRole();
        // Super admin may pass ?restaurantId= to view any restaurant's order;
        // everyone else is evaluated against their own scope.
        String scopeRestaurantId = TenantContext.resolveRestaurantScope(restaurantId);

        var orderOpt = orderService.getById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }
        Order order = orderOpt.get();

        boolean isOwner = userId != null && userId.equals(order.getUserId());
        boolean isSuperAdmin = RoleUtils.hasRole(role, RoleUtils.SUPER_ADMIN);
        boolean isStaff = role != null && !"ROLE_CUSTOMER".equals(role)
                && scopeRestaurantId != null && scopeRestaurantId.equals(order.getRestaurantId());
        if (!isOwner && !isStaff && !isSuperAdmin) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Forbidden"));
        }

        List<OrderItem> items = orderService.itemsFor(orderId);
        List<OrderItemResponse> itemDtos = items.stream().map(OrderItemResponse::from).toList();
        return ResponseEntity.ok(Map.of("success", true, "order", OrderResponse.from(order, items), "items", itemDtos));
    }

    @PostMapping("/status")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> updateStatus(@Valid @RequestBody UpdateOrderStatusRequest req) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(req.restaurantId());
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            Order updated = orderService.updateStatus(
                    req.orderId(), restaurantId, req.status().toUpperCase(),
                    TenantContext.getUserId(), TenantContext.getRole());
            return ResponseEntity.ok(Map.of("success", true, "order", OrderResponse.from(updated, orderService.itemsFor(updated.getId())),
                    "message", "Status updated to " + updated.getOrderStatus()));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Ingredient stock was changed by a concurrent kitchen action — safe to retry.
            return ResponseEntity.status(409).body(Map.of("success", false, "message", "Stock changed concurrently. Please retry."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating order status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Add/update a kitchen note on an order item.
     * POST /api/v1/orders/{orderId}/items/{itemId}/notes  { notes: "Less spicy" }
     */
    @PostMapping("/{orderId}/items/{itemId}/notes")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> updateItemNotes(@PathVariable String orderId,
                                             @PathVariable String itemId,
                                             @RequestBody Map<String, String> body) {
        try {
            String notes = body.getOrDefault("notes", "");
            orderService.updateItemNotes(orderId, itemId, notes);
            return ResponseEntity.ok(Map.of("success", true, "message", "Notes updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Initiate a refund for a paid order.
     */
    @PostMapping("/{orderId}/refund")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> initiateRefund(@PathVariable String orderId,
                                            @RequestBody(required = false) Map<String, String> body) {
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            String restaurantId = TenantContext.resolveRestaurantScope(null);
            String reason = body != null ? body.getOrDefault("reason", "Refund requested") : "Refund requested";

            var refund = orderService.initiateRefund(orderId, userId, role, restaurantId, reason);
            return ResponseEntity.ok(Map.of("success", true, "refund", refund,
                    "message", "Refund initiated — status: " + refund.getRefundStatus()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error initiating refund for order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Audit trail for an order.
     */
    @GetMapping("/{orderId}/audit")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> orderAudit(@PathVariable String orderId) {
        var audit = auditService.getByEntity("ORDER", orderId);
        return ResponseEntity.ok(Map.of("success", true, "audit", audit));
    }

    /**
     * Kitchen production view — batch quantities per dish for the current day.
     */
    @GetMapping("/kitchen/production")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> kitchenProduction(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<Map<String, Object>> production = orderService.getKitchenProduction(restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "production", production));
    }

    /**
     * Delayed orders — orders past their promised pickup time.
     */
    @GetMapping("/kitchen/delayed")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> delayedOrders(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<Map<String, Object>> delayed = orderService.getDelayedOrders(restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "delayedOrders", delayed, "count", delayed.size()));
    }

    /**
     * Cancel an order. Customers can cancel their own NEW orders;
     * staff can cancel any order in a cancellable state.
     */
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelOrder(@PathVariable String orderId,
                                         @RequestBody(required = false) Map<String, String> body) {
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            String restaurantId = TenantContext.resolveRestaurantScope(null);
            String reason = body != null ? body.getOrDefault("reason", "Cancelled") : "Cancelled";

            // Determine if user is order owner or staff
            var orderOpt = orderService.getById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
            }
            Order order = orderOpt.get();
            boolean isOwner = userId != null && userId.equals(order.getUserId());
            boolean isStaff = role != null && !"ROLE_CUSTOMER".equals(role)
                    && restaurantId != null && restaurantId.equals(order.getRestaurantId());

            if (!isOwner && !isStaff) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Not authorized to cancel this order"));
            }

            // Customers can only cancel NEW orders; staff can cancel NEW/PREPARING/PACKED_READY
            String currentStatus = order.getOrderStatus();
            if (isOwner && !"NEW".equals(currentStatus)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                        "Customers can only cancel orders that have not started preparation"));
            }
            if (!"NEW".equals(currentStatus) && !"PREPARING".equals(currentStatus) && !"PACKED_READY".equals(currentStatus)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                        "Order cannot be cancelled from status: " + currentStatus));
            }

            Order updated = orderService.updateStatus(
                    orderId, order.getRestaurantId(), "CANCELLED",
                    userId, role);
            updated.setCancelReason(reason);
            // Note: cancelReason is set in updateStatus via the state change

            return ResponseEntity.ok(Map.of("success", true,
                    "order", OrderResponse.from(updated, orderService.itemsFor(orderId)),
                    "message", "Order cancelled"));
        } catch (Exception e) {
            log.error("Error cancelling order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
