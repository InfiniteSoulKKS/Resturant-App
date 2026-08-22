package com.savorystay.controller;

import com.savorystay.config.OrderStateException;
import com.savorystay.dto.ConfirmPaymentRequest;
import com.savorystay.dto.ErrorResponse;
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
                return ResponseEntity.status(403).body(
                        ErrorResponse.forbidden("Only customer accounts can place orders."));
            }

            String restaurantId;
            if (role != null && !"ROLE_CUSTOMER".equals(role) && !"ROLE_SUPER_ADMIN".equals(role)) {
                restaurantId = TenantContext.getRestaurantId();
            } else {
                restaurantId = req.restaurantId();
            }
            if (restaurantId == null || restaurantId.isBlank()) {
                return ResponseEntity.badRequest().body(
                        ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "restaurantId is required"));
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
        } catch (IllegalArgumentException e) {
            String code = resolveErrorCode(e.getMessage());
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(code, e.getMessage()));
        } catch (Exception e) {
            log.error("Error placing order: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.serverError("Failed to place order. Please try again."));
        }
    }

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
            return ResponseEntity.status(403).body(ErrorResponse.forbidden(e.getMessage()));
        } catch (IllegalArgumentException e) {
            String code = resolveErrorCode(e.getMessage());
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(code, e.getMessage()));
        } catch (Exception e) {
            log.error("Error confirming payment for order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.serverError("Payment could not be confirmed. You can retry."));
        }
    }

    @PostMapping("/{orderId}/mark-paid")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> markPaid(@PathVariable String orderId) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(null);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "No restaurant scope"));
            }
            Order updated = orderService.markCashPaid(orderId, restaurantId, TenantContext.getUserId(), TenantContext.getRole());
            return ResponseEntity.ok(Map.of("success", true,
                    "order", OrderResponse.from(updated, orderService.itemsFor(orderId)),
                    "message", "Cash payment recorded — order marked PAID"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ErrorResponse.forbidden(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("Error marking cash paid for order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.serverError("Failed to record payment."));
        }
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> myOrders() {
        String userId = TenantContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ErrorResponse.unauthorized("Unauthorized"));
        }
        List<Order> orders = orderService.ordersForCustomer(userId);
        Map<String, List<OrderItem>> itemsByOrder = orderService.itemsByOrderIds(orders.stream().map(Order::getId).toList());
        List<OrderResponse> dtos = orders.stream()
                .map(o -> OrderResponse.from(o, itemsByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "orders", dtos));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> restaurantOrders(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "No restaurant scope"));
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
            return ResponseEntity.status(404).body(ErrorResponse.notFound("Order not found"));
        }
        Order order = orderOpt.get();

        boolean isOwner = userId != null && userId.equals(order.getUserId());
        boolean isSuperAdmin = RoleUtils.hasRole(role, RoleUtils.SUPER_ADMIN);
        boolean isStaff = role != null && !"ROLE_CUSTOMER".equals(role)
                && scopeRestaurantId != null && scopeRestaurantId.equals(order.getRestaurantId());
        if (!isOwner && !isStaff && !isSuperAdmin) {
            return ResponseEntity.status(403).body(ErrorResponse.forbidden("You don't have permission to view this order."));
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
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "No restaurant scope"));
            }
            Order updated = orderService.updateStatus(
                    req.orderId(), restaurantId, req.status().toUpperCase(),
                    TenantContext.getUserId(), TenantContext.getRole());
            return ResponseEntity.ok(Map.of("success", true, "order", OrderResponse.from(updated, orderService.itemsFor(updated.getId())),
                    "message", "Status updated to " + updated.getOrderStatus()));
        } catch (OrderStateException e) {
            // P0.3: State machine violations → 409 Conflict
            return ResponseEntity.status(409).body(ErrorResponse.conflict(
                    ErrorResponse.ORDER_INVALID_TRANSITION, e.getMessage()));
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).body(ErrorResponse.conflict(
                    ErrorResponse.INVENTORY_RESERVATION_CONFLICT, "Stock changed concurrently. Please retry."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ErrorResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating order status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, e.getMessage()));
        }
    }

    @PostMapping("/{orderId}/items/{itemId}/notes")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> updateItemNotes(@PathVariable String orderId,
                                             @PathVariable String itemId,
                                             @RequestBody Map<String, String> body) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(null);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "No restaurant scope"));
            }
            var order = orderService.getById(orderId);
            if (order.isEmpty() || !restaurantId.equals(order.get().getRestaurantId())) {
                return ResponseEntity.status(403).body(ErrorResponse.forbidden("Order not found in this restaurant"));
            }
            orderService.updateItemNotes(orderId, itemId, body.getOrDefault("notes", ""));
            return ResponseEntity.ok(Map.of("success", true, "message", "Notes updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, e.getMessage()));
        }
    }

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
            return ResponseEntity.status(403).body(ErrorResponse.forbidden(e.getMessage()));
        } catch (IllegalArgumentException e) {
            String code = e.getMessage() != null && e.getMessage().contains("refund") ? ErrorResponse.REFUND_ALREADY_EXISTS : ErrorResponse.VALIDATION_ERROR;
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(code, e.getMessage()));
        } catch (Exception e) {
            log.error("Error initiating refund for order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.serverError("Failed to initiate refund."));
        }
    }

    @GetMapping("/{orderId}/audit")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> orderAudit(@PathVariable String orderId) {
        String restaurantId = TenantContext.resolveRestaurantScope(null);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "No restaurant scope"));
        }
        var order = orderService.getById(orderId);
        if (order.isEmpty() || !restaurantId.equals(order.get().getRestaurantId())) {
            return ResponseEntity.status(403).body(ErrorResponse.forbidden("Order not found in this restaurant"));
        }
        var audit = auditService.getByEntity("ORDER", orderId);
        return ResponseEntity.ok(Map.of("success", true, "audit", audit));
    }

    @GetMapping("/kitchen/production")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> kitchenProduction(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "No restaurant scope"));
        }
        return ResponseEntity.ok(Map.of("success", true, "production", orderService.getKitchenProduction(restaurantId)));
    }

    @GetMapping("/kitchen/delayed")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> delayedOrders(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, "No restaurant scope"));
        }
        List<Map<String, Object>> delayed = orderService.getDelayedOrders(restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "delayedOrders", delayed, "count", delayed.size()));
    }

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
                return ResponseEntity.status(404).body(ErrorResponse.notFound("Order not found"));
            }
            Order order = orderOpt.get();

            boolean isOwner = userId != null && userId.equals(order.getUserId());
            boolean isStaff = role != null && !"ROLE_CUSTOMER".equals(role)
                    && restaurantId != null && restaurantId.equals(order.getRestaurantId());

            if (!isOwner && !isStaff) {
                return ResponseEntity.status(403).body(ErrorResponse.forbidden("Not authorized to cancel this order"));
            }
            if (isOwner && !"NEW".equals(order.getOrderStatus())) {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest(
                        ErrorResponse.ORDER_INVALID_TRANSITION,
                        "Customers can only cancel orders that have not started preparation"));
            }

            OrderStateMachine.validate(order.getOrderStatus(), "CANCELLED", role);

            Order updated = orderService.updateStatus(orderId, order.getRestaurantId(), "CANCELLED", userId, role);
            return ResponseEntity.ok(Map.of("success", true,
                    "order", OrderResponse.from(updated, orderService.itemsFor(orderId)),
                    "message", "Order cancelled"));
        } catch (OrderStateException e) {
            return ResponseEntity.status(409).body(ErrorResponse.conflict(
                    ErrorResponse.ORDER_INVALID_TRANSITION, e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(ErrorResponse.forbidden(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest(ErrorResponse.VALIDATION_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("Error cancelling order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.serverError("Failed to cancel order."));
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────

    private static String resolveErrorCode(String message) {
        if (message == null) return ErrorResponse.VALIDATION_ERROR;
        String lower = message.toLowerCase();
        if (lower.contains("plate") || lower.contains("sold out")) return ErrorResponse.PLATE_CAPACITY_EXCEEDED;
        if (lower.contains("table") && lower.contains("available")) return ErrorResponse.TABLE_SLOT_FULL;
        if (lower.contains("cutoff")) return ErrorResponse.PREORDER_CUTOFF_PASSED;
        if (lower.contains("closed")) return ErrorResponse.PREORDER_RESTAURANT_CLOSED;
        if (lower.contains("not available") || lower.contains("sold out")) return ErrorResponse.DISH_NOT_AVAILABLE;
        if (lower.contains("payment") && lower.contains("amount")) return ErrorResponse.PAYMENT_AMOUNT_MISMATCH;
        if (lower.contains("cash") && lower.contains("cannot")) return ErrorResponse.PAYMENT_FAILED;
        if (lower.contains("not found")) return ErrorResponse.RESOURCE_NOT_FOUND;
        if (lower.contains("not authorized") || lower.contains("forbidden")) return ErrorResponse.AUTH_FORBIDDEN;
        return ErrorResponse.VALIDATION_ERROR;
    }
}
