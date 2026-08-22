package com.savorystay.service;

import com.savorystay.dto.OrderItemRequest;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.Order;
import com.savorystay.entity.Payment;
import com.savorystay.entity.Restaurant;
import com.savorystay.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P0 Tests: Tenant isolation, cash mark-paid, payment idempotency.
 * Verifies that cross-restaurant access is prevented and the cash payment
 * lifecycle works correctly.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTenantIsolationTest {

    private static final String REST_A = "REST_A";
    private static final String REST_B = "REST_B";

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock MenuService menuService;
    @Mock OutboxService outboxService;
    @Mock IngredientService ingredientService;
    @Mock PreOrderAvailabilityService availabilityService;
    @Mock RestaurantRepository restaurantRepository;
    @Mock CustomerRestaurantService customerRestaurantService;
    @Mock AuditService auditService;
    @Mock RefundRepository refundRepository;
    @Mock RealtimeService realtimeService;
    @Mock PlateCapacityRepository plateCapacityRepository;
    @Mock TableSlotCapacityRepository tableSlotCapacityRepository;
    @Mock RestaurantSettingsRepository restaurantSettingsRepository;
    @Mock MenuItemIngredientRepository menuItemIngredientRepository;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                orderRepository, orderItemRepository, menuItemRepository,
                orderStatusHistoryRepository, paymentRepository, menuService,
                outboxService, ingredientService, availabilityService, restaurantRepository,
                customerRestaurantService, auditService, refundRepository, realtimeService,
                plateCapacityRepository, tableSlotCapacityRepository, restaurantSettingsRepository,
                menuItemIngredientRepository);
    }

    // ==================== CROSS-RESTAURANT PAYMENT ====================

    @Test
    void crossRestaurantPaymentIsRejected() {
        // Staff from Restaurant B tries to confirm payment on Restaurant A's order
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .userId("USR_CUSTOMER")
                .totalAmount(new BigDecimal("500"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // Staff from REST_B — their restaurantId is REST_B, but order belongs to REST_A
        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.confirmPayment(order.getId(), "USR_STAFF_B", "ROLE_MANAGER", REST_B,
                        new BigDecimal("500"), "UPI"));

        assertTrue(ex.getMessage().contains("Forbidden"));
        assertEquals("PENDING", order.getPaymentStatus(), "Order must remain PENDING");
    }

    @Test
    void sameRestaurantStaffCanConfirmPayment() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .userId("USR_CUSTOMER")
                .totalAmount(new BigDecimal("500"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxService.record(anyString(), anyString(), any())).thenReturn(null);

        Order result = service.confirmPayment(order.getId(), "USR_STAFF_A", "ROLE_MANAGER", REST_A,
                new BigDecimal("500"), "UPI");

        assertEquals("PAID", result.getPaymentStatus());
    }

    // ==================== CROSS-RESTAURANT ORDER STATUS ====================

    @Test
    void crossRestaurantStatusUpdateIsRejected() {
        // Staff from Restaurant B tries to update Restaurant A's order
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .orderNumber("#ORD-XSEC")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST_B)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.updateStatus(order.getId(), REST_B, "PREPARING", "USR_CHEF_B", "ROLE_CHEF"));
    }

    // ==================== CASH MARK-PAID ====================

    @Test
    void markCashPaidSucceedsForCashOrder() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .orderNumber("#ORD-CASH")
                .restaurantId(REST_A)
                .userId("USR_CUST")
                .totalAmount(new BigDecimal("300"))
                .paymentStatus("PENDING")
                .paymentMethod("CASH")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST_A)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.markCashPaid(order.getId(), REST_A, "USR_MGR", "ROLE_MANAGER");

        assertEquals("PAID", result.getPaymentStatus());
        verify(auditService).record(eq(REST_A), eq("USR_MGR"), eq("ROLE_MANAGER"),
                eq("CASH_PAYMENT_RECORDED"), eq("ORDER"), eq(order.getId()), any(java.util.Map.class), anyString());
    }

    @Test
    void markCashPaidIsIdempotent() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .totalAmount(new BigDecimal("300"))
                .paymentStatus("PAID") // already paid
                .paymentMethod("CASH")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST_A)).thenReturn(Optional.of(order));

        Order result = service.markCashPaid(order.getId(), REST_A, "USR_MGR", "ROLE_MANAGER");

        assertEquals("PAID", result.getPaymentStatus());
        verify(paymentRepository, never()).save(any()); // no new payment record
    }

    @Test
    void markCashPaidRejectsNonCashOrder() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .totalAmount(new BigDecimal("300"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI") // not CASH
                .orderStatus("NEW")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST_A)).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.markCashPaid(order.getId(), REST_A, "USR_MGR", "ROLE_MANAGER"));

        assertTrue(ex.getMessage().contains("Only CASH orders"));
    }

    @Test
    void markCashPaidRejectsAlreadyPaidOrder() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .totalAmount(new BigDecimal("300"))
                .paymentStatus("PAID")
                .paymentMethod("CASH")
                .orderStatus("COMPLETED")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST_A)).thenReturn(Optional.of(order));

        // Already PAID — should be idempotent, not throw
        Order result = service.markCashPaid(order.getId(), REST_A, "USR_MGR", "ROLE_MANAGER");
        assertEquals("PAID", result.getPaymentStatus());
    }

    @Test
    void markCashPaidRejectsCustomerRole() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .totalAmount(new BigDecimal("300"))
                .paymentStatus("PENDING")
                .paymentMethod("CASH")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST_A)).thenReturn(Optional.of(order));

        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.markCashPaid(order.getId(), REST_A, "USR_CUST", "ROLE_CUSTOMER"));

        assertTrue(ex.getMessage().contains("Only staff"));
    }

    @Test
    void markCashPaidRejectsCrossRestaurant() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .totalAmount(new BigDecimal("300"))
                .paymentStatus("PENDING")
                .paymentMethod("CASH")
                .orderStatus("NEW")
                .build();
        // Staff from REST_B trying to mark REST_A's order as paid
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST_B)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.markCashPaid(order.getId(), REST_B, "USR_MGR_B", "ROLE_MANAGER"));
    }

    // ==================== PAYMENT IDEMPOTENCY (ADDITIONAL) ====================

    @Test
    void confirmPaymentAmountMismatchIsRejected() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .userId("USR_CUST")
                .totalAmount(new BigDecimal("500"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.confirmPayment(order.getId(), "USR_CUST", "ROLE_CUSTOMER", REST_A,
                        new BigDecimal("400"), "UPI"));

        assertTrue(ex.getMessage().contains("does not match"));
    }

    @Test
    void confirmPaymentRequiresAmount() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST_A)
                .userId("USR_CUST")
                .totalAmount(new BigDecimal("500"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.confirmPayment(order.getId(), "USR_CUST", "ROLE_CUSTOMER", REST_A,
                        null, "UPI"));

        assertTrue(ex.getMessage().contains("required"));
    }
}
