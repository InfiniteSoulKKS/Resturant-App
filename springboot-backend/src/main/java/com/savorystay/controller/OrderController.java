package com.savorystay.controller;

import com.savorystay.dto.ConfirmPaymentRequest;
import com.savorystay.dto.OrderItemResponse;
import com.savorystay.dto.OrderResponse;
import com.savorystay.dto.PlaceOrderRequest;
import com.savorystay.dto.UpdateOrderStatusRequest;
import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;
import com.savorystay.common.OrderStateMachine;
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody PlaceOrderRequest req) {
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();

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
     *
     * P0 FIX: Uses orderRepository.findByIdAndRestaurantId() for tenant safety.
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

    /**
     * Staff-only cash payment completion.
     * POST /api/v1/orders/{orderId}/mark-paid
     *
     * P0 IMPLEMENTATION: Allows staff to mark CASH orders as paid when the customer
     * pays at the counter. Only CASH/PENDING orders can be marked paid.
     * Only restaurant staff (manager/admin/super-admin) can perform this action.
     * Cross-tenant access is prevented via restaurantId validation.
     */
    @PostMapping("/{orderId}/mark-paid")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> markPaid(@PathVariable String orderId) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(null);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();

            Order updated = orderService.markCashPaid(orderId, restaurantId, userId, role);

            return ResponseEntity.ok(Map.of("success", true,
                    "order", OrderResponse.from(updated, orderService.itemsFor(orderId)),
                    "message", "Cash payment recorded — order marked PAID"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error marking cash paid for order {}: {}", orderId, e.getMessage(), e);
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
     * Restaurant order queue — STAFF ONLY.
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
     *
     * P0 FIX: Verifies order belongs to the caller's restaurant.
     */
    @PostMapping("/{orderId}/items/{itemId}/notes")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> updateItemNotes(@PathVariable String orderId,
                                             @PathVariable String itemId,
                                             @RequestBody Map<String, String> body) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(null);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }

            // Tenant isolation: verify the order belongs to this restaurant
            var order = orderService.getById(orderId);
            if (order.isEmpty() || !restaurantId.equals(order.get().getRestaurantId())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Order not found in this restaurant"));
            }

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
     *
     * P0 FIX: Verifies the order belongs to the caller's restaurant before returning audit data.
     */
    @GetMapping("/{orderId}/audit")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> orderAudit(@PathVariable String orderId) {
        String restaurantId = TenantContext.resolveRestaurantScope(null);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        // Tenant isolation: verify the order belongs to this restaurant
        var order = orderService.getById(orderId);
        if (order.isEmpty() || !restaurantId.equals(order.get().getRestaurantId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Order not found in this restaurant"));
        }

        var audit = auditService.getByEntity("ORDER", orderId);
        return ResponseEntity.ok(Map.of("success", true, "audit", audit));
    }

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
     *
     * P0 FIX: Delegates entirely to OrderStateMachine for transition validation.
     * Removes duplicate state-checking logic that existed in this controller.
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

            var orderOpt = orderService.getById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
            }
            Order order = orderOpt.get();

            // Tenant isolation: verify ownership or staff membership
            boolean isOwner = userId != null && userId.equals(order.getUserId());
            boolean isStaff = role != null && !"ROLE_CUSTOMER".equals(role)
                    && restaurantId != null && restaurantId.equals(order.getRestaurantId());

            if (!isOwner && !isStaff) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Not authorized to cancel this order"));
            }

            // Customer restriction: only NEW orders can be cancelled by the owner
            if (isOwner && !"NEW".equals(order.getOrderStatus())) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                        "Customers can only cancel orders that have not started preparation"));
            }

            // Delegate to OrderStateMachine for transition + role validation
            // This replaces the duplicate state-checking logic that was here before.
            OrderStateMachine.validate(order.getOrderStatus(), "CANCELLED", role);

            Order updated = orderService.updateStatus(
                    orderId, order.getRestaurantId(), "CANCELLED",
                    userId, role);

            return ResponseEntity.ok(Map.of("success", true,
                    "order", OrderResponse.from(updated, orderService.itemsFor(orderId)),
                    "message", "Order cancelled"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error cancelling order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
