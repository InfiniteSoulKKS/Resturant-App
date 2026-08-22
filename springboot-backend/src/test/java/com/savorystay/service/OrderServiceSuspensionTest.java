package com.savorystay.service;

import com.savorystay.dto.OrderItemRequest;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.Order;
import com.savorystay.entity.Restaurant;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.OrderStatusHistoryRepository;
import com.savorystay.repository.PaymentRepository;
import com.savorystay.repository.*;
import com.savorystay.service.AuditService;
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
 * Unit tests for the suspended-restaurant order gate in
 * {@link OrderService#placeOrder}: a restaurant whose status is not ACTIVE must
 * not accept orders — customers hitting the API directly (or stale cached
 * menus) get a clear business error instead of an unfulfillable order.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceSuspensionTest {

    private static final String REST = "REST_TEST";

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

    private List<OrderItemRequest> items() {
        return List.of(new OrderItemRequest("MI_1", 2, null));
    }

    private Restaurant restaurant(String status) {
        return Restaurant.builder().id(REST).name("Test Diner").status(status).build();
    }

    @Test
    void suspendedRestaurantBlocksOrderPlacement() {
        when(restaurantRepository.findById(REST)).thenReturn(Optional.of(restaurant("SUSPENDED")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.placeOrder("USR_1", REST, "Customer", null, null,
                        "PICKUP", null, null, null, null, items(), "MOCK"));

        assertTrue(ex.getMessage().contains("offline"), "message should mention offline: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("SUSPENDED"));
    }

    @Test
    void missingRestaurantBlocksOrderPlacement() {
        when(restaurantRepository.findById(REST)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.placeOrder("USR_1", REST, "Customer", null, null,
                        "PICKUP", null, null, null, null, items(), "MOCK"));
    }

    @Test
    void activeRestaurantAllowsOrderPlacement() {
        when(restaurantRepository.findById(REST)).thenReturn(Optional.of(restaurant("ACTIVE")));
        when(menuItemRepository.findByIdAndRestaurantId("MI_1", REST)).thenReturn(Optional.of(
                MenuItem.builder().id("MI_1").restaurantId(REST).title("Butter Chicken")
                        .price(new BigDecimal("450")).status("Available").build()));
        when(menuService.getEffectivePrice(anyString(), any())).thenReturn(new BigDecimal("450"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = service.placeOrder("USR_1", REST, "Customer", null, null,
                "PICKUP", null, null, null, null, items(), "MOCK");

        assertEquals("NEW", order.getOrderStatus());
        assertEquals("PENDING", order.getPaymentStatus());
        assertEquals(0, new BigDecimal("900").compareTo(order.getTotalAmount()));
    }

    // ==================== AUTO-JOIN ====================

    @Test
    void autoJoinAddsCustomerOnFirstOrder() {
        Restaurant rest = restaurant("ACTIVE");
        rest.setAutoJoinCustomers(true);
        when(restaurantRepository.findById(REST)).thenReturn(Optional.of(rest));
        when(menuItemRepository.findByIdAndRestaurantId("MI_1", REST)).thenReturn(Optional.of(
                MenuItem.builder().id("MI_1").restaurantId(REST).title("Biryani")
                        .price(new BigDecimal("380")).status("Available").build()));
        when(menuService.getEffectivePrice(anyString(), any())).thenReturn(new BigDecimal("380"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRestaurantService.isMember("USR_1", REST)).thenReturn(false);

        service.placeOrder("USR_1", REST, "Customer", null, null,
                "PICKUP", null, null, null, null, items(), "MOCK");

        org.mockito.Mockito.verify(customerRestaurantService).join("USR_1", REST, null);
    }

    @Test
    void autoJoinSkipsIfAlreadyMember() {
        Restaurant rest = restaurant("ACTIVE");
        rest.setAutoJoinCustomers(true);
        when(restaurantRepository.findById(REST)).thenReturn(Optional.of(rest));
        when(menuItemRepository.findByIdAndRestaurantId("MI_1", REST)).thenReturn(Optional.of(
                MenuItem.builder().id("MI_1").restaurantId(REST).title("Biryani")
                        .price(new BigDecimal("380")).status("Available").build()));
        when(menuService.getEffectivePrice(anyString(), any())).thenReturn(new BigDecimal("380"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRestaurantService.isMember("USR_1", REST)).thenReturn(true);

        service.placeOrder("USR_1", REST, "Customer", null, null,
                "PICKUP", null, null, null, null, items(), "MOCK");

        org.mockito.Mockito.verify(customerRestaurantService, org.mockito.Mockito.never())
                .join(anyString(), anyString(), any());
    }

    @Test
    void autoJoinDisabledSkipsJoin() {
        Restaurant rest = restaurant("ACTIVE");
        rest.setAutoJoinCustomers(false);
        when(restaurantRepository.findById(REST)).thenReturn(Optional.of(rest));
        when(menuItemRepository.findByIdAndRestaurantId("MI_1", REST)).thenReturn(Optional.of(
                MenuItem.builder().id("MI_1").restaurantId(REST).title("Biryani")
                        .price(new BigDecimal("380")).status("Available").build()));
        when(menuService.getEffectivePrice(anyString(), any())).thenReturn(new BigDecimal("380"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.placeOrder("USR_1", REST, "Customer", null, null,
                "PICKUP", null, null, null, null, items(), "MOCK");

        org.mockito.Mockito.verify(customerRestaurantService, org.mockito.Mockito.never())
                .join(anyString(), anyString(), any());
    }

    // ==================== CASH PAYMENT ====================

    private Order cashOrder() {
        return Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("900"))
                .paymentStatus("PENDING")
                .paymentMethod("CASH")
                .orderStatus("NEW")
                .build();
    }

    @Test
    void confirmPaymentRejectsCashOrders() {
        Order order = cashOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.confirmPayment(order.getId(), "USR_1", "ROLE_CUSTOMER", REST,
                        new BigDecimal("900"), "CASH"));

        assertTrue(ex.getMessage().contains("CASH"),
                "message should mention CASH: " + ex.getMessage());
        assertEquals("PENDING", order.getPaymentStatus(), "order must remain PENDING");
    }

    @Test
    void confirmPaymentRejectsCashGatewayForNonCashOrder() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.confirmPayment(order.getId(), "USR_1", "ROLE_CUSTOMER", REST,
                        new BigDecimal("450"), "CASH"));

        assertTrue(ex.getMessage().contains("CASH"));
        assertEquals("PENDING", order.getPaymentStatus());
    }

    @Test
    void confirmPaymentSucceedsForOnlineGateway() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxService.record(anyString(), anyString(), any())).thenReturn(null);

        Order result = service.confirmPayment(order.getId(), "USR_1", "ROLE_CUSTOMER", REST,
                new BigDecimal("450"), "UPI");

        assertEquals("PAID", result.getPaymentStatus());
    }

    // ==================== PAYMENT IDEMPOTENCY ====================

    @Test
    void confirmPaymentIsIdempotentWhenAlreadyPaid() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PAID")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        Order result = service.confirmPayment(order.getId(), "USR_1", "ROLE_CUSTOMER", REST,
                new BigDecimal("450"), "UPI");

        // Should return the order without creating a new payment
        assertEquals("PAID", result.getPaymentStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirmPaymentIsIdempotentWhenPaymentRowExists() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // Payment already exists
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(List.of(
                com.savorystay.entity.Payment.builder().transactionId("TXN_1").build()));

        Order result = service.confirmPayment(order.getId(), "USR_1", "ROLE_CUSTOMER", REST,
                new BigDecimal("450"), "UPI");

        assertEquals("PAID", result.getPaymentStatus());
        // Should NOT create a new payment record
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirmPaymentRejectsAmountMismatch() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PENDING")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.confirmPayment(order.getId(), "USR_1", "ROLE_CUSTOMER", REST,
                        new BigDecimal("300"), "UPI"));

        assertTrue(ex.getMessage().contains("does not match"));
        assertEquals("PENDING", order.getPaymentStatus());
    }

    // ==================== CANCELLATION ====================

    @Test
    void cancelNewOrderSetsCancelledStatus() {
        String orderId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .id(orderId)
                .orderNumber("#ORD-TEST")
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PENDING")
                .paymentMethod("CASH")
                .orderStatus("NEW")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxService.record(anyString(), anyString(), any())).thenReturn(null);

        Order result = service.updateStatus(order.getId(), REST, "CANCELLED", "USR_MGR", "ROLE_MANAGER");

        assertEquals("CANCELLED", result.getOrderStatus());
        assertEquals("USR_MGR", result.getCancelledBy());
        assertNotNull(result.getCancelledAt());
    }

    @Test
    void cancelAlreadyCancelledOrderIsRejected() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PENDING")
                .paymentMethod("CASH")
                .orderStatus("CANCELLED")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST)).thenReturn(Optional.of(order));

        com.savorystay.config.OrderStateException ex = assertThrows(com.savorystay.config.OrderStateException.class, () ->
                service.updateStatus(order.getId(), REST, "CANCELLED", "USR_MGR", "ROLE_MANAGER"));

        assertTrue(ex.getMessage().contains("cannot move"));
    }

    @Test
    void customerCannotCancelPreparingOrder() {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .restaurantId(REST)
                .userId("USR_1")
                .customerName("Customer")
                .totalAmount(new BigDecimal("450"))
                .paymentStatus("PENDING")
                .paymentMethod("CASH")
                .orderStatus("PREPARING")
                .build();
        when(orderRepository.findByIdAndRestaurantId(order.getId(), REST)).thenReturn(Optional.of(order));

        // Chef role cannot cancel from PREPARING
        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.updateStatus(order.getId(), REST, "CANCELLED", "USR_CHF", "ROLE_CHEF"));

        assertTrue(ex.getMessage().contains("not authorized"));
    }
}
