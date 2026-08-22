package com.savorystay.service;

import com.savorystay.entity.Order;
import com.savorystay.entity.Payment;
import com.savorystay.entity.Refund;
import com.savorystay.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the refund lifecycle in {@link OrderService}.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final String REST = "REST_01";
    private static final String OTHER_REST = "REST_02";
    private static final String USER_ID = "USR_001";
    private static final String ORDER_ID = "ORD_TEST";
    private static final String PAYMENT_ID = "TXN_TEST";

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock com.savorystay.repository.MenuItemRepository menuItemRepository;
    @Mock com.savorystay.repository.OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock MenuService menuService;
    @Mock OutboxService outboxService;
    @Mock IngredientService ingredientService;
    @Mock PreOrderAvailabilityService availabilityService;
    @Mock com.savorystay.repository.RestaurantRepository restaurantRepository;
    @Mock CustomerRestaurantService customerRestaurantService;
    @Mock AuditService auditService;
    @Mock RefundRepository refundRepository;
    @Mock RealtimeService realtimeService;
    @Mock PlateCapacityRepository plateCapacityRepository;
    @Mock TableSlotCapacityRepository tableSlotCapacityRepository;
    @Mock RestaurantSettingsRepository restaurantSettingsRepository;
    @Mock com.savorystay.repository.MenuItemIngredientRepository menuItemIngredientRepository;

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

    private Order order(String status, String paymentStatus, String restaurantId) {
        return Order.builder()
                .id(ORDER_ID)
                .restaurantId(restaurantId)
                .totalAmount(new BigDecimal("500.00"))
                .orderStatus(status)
                .paymentStatus(paymentStatus)
                .paymentMethod("UPI")
                .userId(USER_ID)
                .orderNumber("#ORD-TEST")
                .build();
    }

    private Payment payment(String orderId, String gateway) {
        return Payment.builder()
                .transactionId(PAYMENT_ID)
                .orderId(orderId)
                .gateway(gateway)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .paymentStatus("PAID")
                .build();
    }

    // ─── INITIATE REFUND ──────────────────────────────────────────

    @Test
    void initiateRefundOnPaidOrderCreatesRefundRecord() {
        Order o = order("COMPLETED", "PAID", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(payment(ORDER_ID, "UPI")));
        when(refundRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(refundRepository.save(any())).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            if (r.getId() == null) r.setId("REF_GENERATED");
            return r;
        });
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Refund refund = service.initiateRefund(ORDER_ID, "MGR_001", "ROLE_MANAGER", REST, "Customer unhappy");

        assertEquals("REQUESTED", refund.getRefundStatus());
        assertEquals(ORDER_ID, refund.getOrderId());
        assertEquals(new BigDecimal("500.00"), refund.getAmount());
        assertEquals("UPI", refund.getGateway());
        assertEquals("MGR_001", refund.getInitiatedBy());
        assertEquals("Customer unhappy", refund.getReason());
        assertEquals("REFUND_PENDING", o.getPaymentStatus());
        verify(auditService).record(eq(REST), eq("MGR_001"), eq("ROLE_MANAGER"),
                eq("REFUND_INITIATED"), eq("ORDER"), eq(ORDER_ID),
                any(java.util.Map.class), eq("Customer unhappy"));
    }

    @Test
    void initiateRefundIsIdempotentWhenCompletedExists() {
        // Order must be PAID for the PAID check to pass (idempotent check is AFTER the PAID check)
        Order o = order("COMPLETED", "PAID", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));

        Refund existing = Refund.builder()
                .id("REF_EXISTING").orderId(ORDER_ID)
                .refundStatus("COMPLETED").amount(new BigDecimal("500.00"))
                .restaurantId(REST)
                .build();
        when(refundRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(existing));

        Refund refund = service.initiateRefund(ORDER_ID, "MGR_001", "ROLE_MANAGER", REST, "Retry");

        assertEquals("REF_EXISTING", refund.getId());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void initiateRefundIsIdempotentWhenProcessingExists() {
        Order o = order("COMPLETED", "PAID", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));

        Refund existing = Refund.builder()
                .id("REF_PROCESSING").orderId(ORDER_ID)
                .refundStatus("PROCESSING").amount(new BigDecimal("500.00"))
                .restaurantId(REST)
                .build();
        when(refundRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(existing));

        Refund refund = service.initiateRefund(ORDER_ID, "MGR_001", "ROLE_MANAGER", REST, "Retry");

        assertEquals("REF_PROCESSING", refund.getId());
        verify(refundRepository, never()).save(any());
    }

    // ─── REJECT NON-PAID ──────────────────────────────────────────

    @Test
    void initiateRefundRejectsNonPaidOrder() {
        Order o = order("COMPLETED", "PENDING", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.initiateRefund(ORDER_ID, "MGR_001", "ROLE_MANAGER", REST, "reason"));

        assertTrue(ex.getMessage().contains("not PAID"));
        verify(refundRepository, never()).save(any());
    }

    @Test
    void initiateRefundRejectsPendingPaymentOrder() {
        Order o = order("NEW", "PENDING", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.initiateRefund(ORDER_ID, "MGR_001", "ROLE_MANAGER", REST, "reason"));

        assertTrue(ex.getMessage().contains("not PAID"));
    }

    // ─── REJECT CUSTOMER ROLE ─────────────────────────────────────

    @Test
    void initiateRefundRejectsCustomerRole() {
        Order o = order("COMPLETED", "PAID", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.initiateRefund(ORDER_ID, USER_ID, "ROLE_CUSTOMER", REST, "reason"));

        assertTrue(ex.getMessage().contains("Only staff"));
        verify(refundRepository, never()).save(any());
    }

    @Test
    void initiateRefundRejectsNullRole() {
        Order o = order("COMPLETED", "PAID", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.initiateRefund(ORDER_ID, USER_ID, null, REST, "reason"));

        assertTrue(ex.getMessage().contains("Only staff"));
    }

    // ─── REJECT CROSS-TENANT ─────────────────────────────────────

    @Test
    void initiateRefundRejectsCrossTenantAccess() {
        Order o = order("COMPLETED", "PAID", OTHER_REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.initiateRefund(ORDER_ID, "MGR_001", "ROLE_MANAGER", REST, "reason"));

        assertTrue(ex.getMessage().contains("does not belong"));
    }

    // ─── COMPLETE REFUND ──────────────────────────────────────────

    @Test
    void completeRefundMarksCompletedAndUpdatesOrderAndPayment() {
        Refund refund = Refund.builder()
                .id("REF_001").orderId(ORDER_ID).paymentId(PAYMENT_ID)
                .amount(new BigDecimal("500.00")).refundStatus("REQUESTED")
                .restaurantId(REST).gateway("UPI")
                .build();
        when(refundRepository.findById("REF_001")).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order o = order("COMPLETED", "REFUND_PENDING", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment p = payment(ORDER_ID, "UPI");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(p));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Refund completed = service.completeRefund("REF_001", "stripe_re_123");

        assertEquals("COMPLETED", completed.getRefundStatus());
        assertEquals("stripe_re_123", completed.getProviderRefundId());
        assertNotNull(completed.getCompletedAt());
        assertEquals("REFUNDED", o.getPaymentStatus());
        assertEquals("REFUNDED", p.getPaymentStatus());
        verify(auditService).record(eq(REST), eq("SYSTEM"), isNull(),
                eq("REFUND_COMPLETED"), eq("REFUND"), eq("REF_001"),
                any(java.util.Map.class), any(String.class));
    }

    @Test
    void completeRefundIsIdempotentWhenAlreadyCompleted() {
        Refund refund = Refund.builder()
                .id("REF_001").orderId(ORDER_ID)
                .refundStatus("COMPLETED").build();
        when(refundRepository.findById("REF_001")).thenReturn(Optional.of(refund));

        Refund result = service.completeRefund("REF_001", "stripe_re_123");

        assertEquals("REF_001", result.getId());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void completeRefundHandlesMissingPaymentGracefully() {
        Refund refund = Refund.builder()
                .id("REF_001").orderId(ORDER_ID).paymentId("UNKNOWN")
                .amount(new BigDecimal("500.00"))
                .refundStatus("REQUESTED").restaurantId(REST)
                .gateway("UPI")
                .build();
        when(refundRepository.findById("REF_001")).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order o = order("COMPLETED", "REFUND_PENDING", REST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(o));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Refund completed = service.completeRefund("REF_001", null);

        assertEquals("COMPLETED", completed.getRefundStatus());
    }
}
