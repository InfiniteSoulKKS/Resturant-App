package com.savorystay.integration;

import com.savorystay.entity.AuditTrail;
import com.savorystay.entity.Ingredient;
import com.savorystay.entity.InventoryLedger;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;
import com.savorystay.entity.OrderStatusHistory;
import com.savorystay.entity.Payment;
import com.savorystay.entity.Refund;
import com.savorystay.entity.Restaurant;
import com.savorystay.entity.User;
import com.savorystay.repository.*;
import com.savorystay.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.mail.internet.MimeMessage;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for refund, audit trail, and order cancellation flows
 * against a real MySQL database via Testcontainers.
 *
 * Auto-skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.kafka.auto-offset-reset=earliest",
        // Disable Kafka/Redis/Mail to avoid infra dependencies
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefundAuditIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("savorystay_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        // Bypass secret validation in tests
        registry.add("jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long-for-hmac");
    }

    @Autowired RestaurantRepository restaurantRepository;
    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired RefundRepository refundRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired MenuItemRepository menuItemRepository;
    @Autowired MenuItemIngredientRepository menuItemIngredientRepository;
    @Autowired AuditTrailRepository auditTrailRepository;
    @Autowired InventoryLedgerRepository inventoryLedgerRepository;
    @Autowired OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired OrderService orderService;
    @Autowired IngredientService ingredientService;
    @Autowired AuditService auditService;

    @MockBean
    JavaMailSender javaMailSender;
    @MockBean
    org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @MockBean
    org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
    @MockBean
    com.savorystay.config.DataSeeder dataSeeder;
    @MockBean
    com.savorystay.config.DatabaseSchemaMigration databaseSchemaMigration;

    private static final String REST_ID = "REST_INT_TEST";
    private static final String USER_MGR = "USR_MGR_INT";
    private static final String USER_CUST = "USR_CUST_INT";
    private static final String USER_CHEF = "USR_CHEF_INT";

    @BeforeAll
    static void seedData(@Autowired org.springframework.security.crypto.password.PasswordEncoder encoder,
                          @Autowired UserRepository userRepository,
                          @Autowired RestaurantRepository restaurantRepository) {
        // Create restaurant
        restaurantRepository.save(Restaurant.builder()
                .id(REST_ID).name("Integration Test Restaurant").status("ACTIVE")
                .currency("INR").build());

        // Create users
        userRepository.save(User.builder()
                .id(USER_MGR).username("int_manager").email("mgr@int.test")
                .passwordHash(encoder.encode("password")).role("ROLE_MANAGER")
                .restaurantId(REST_ID).enabled(true).build());
        userRepository.save(User.builder()
                .id(USER_CUST).username("int_customer").email("cust@int.test")
                .passwordHash(encoder.encode("password")).role("ROLE_CUSTOMER")
                .enabled(true).build());
        userRepository.save(User.builder()
                .id(USER_CHEF).username("int_chef").email("chef@int.test")
                .passwordHash(encoder.encode("password")).role("ROLE_CHEF")
                .restaurantId(REST_ID).enabled(true).build());
    }

    @AfterEach
    void cleanup() {
        // Clean up test data between tests
        outboxEventRepository.deleteAll();
        orderStatusHistoryRepository.deleteAll();
        inventoryLedgerRepository.deleteAll();
        auditTrailRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemIngredientRepository.deleteAll();
        menuItemRepository.deleteAll();
        ingredientRepository.deleteAll();
    }

    // ─── HELPERS ──────────────────────────────────────────────────

    private Ingredient createIngredient(String name, String unit, BigDecimal stock) {
        Ingredient ing = Ingredient.builder()
                .restaurantId(REST_ID).name(name).displayName(name)
                .normalizedName(name.toLowerCase().trim())
                .unit(unit)
                .stockQuantity(stock).reorderLevel(BigDecimal.TEN)
                .active(true).build();
        return ingredientRepository.save(ing);
    }

    private Order createOrder(String status, String paymentStatus, String paymentMethod) {
        Order order = Order.builder()
                .restaurantId(REST_ID)
                .userId(USER_CUST).customerName("Test Customer")
                .customerPhone("+919999999999").customerEmail("cust@int.test")
                .totalAmount(new BigDecimal("500.00"))
                .orderType("PICKUP").orderStatus(status)
                .paymentStatus(paymentStatus).paymentMethod(paymentMethod)
                .build();
        return orderRepository.save(order);
    }

    // ─── REFUND INTEGRATION TESTS ─────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Refund: full lifecycle — initiate → complete")
    void refundFullLifecycle() {
        // Create a PAID order
        Order order = createOrder("COMPLETED", "PAID", "UPI");

        // Create a payment record
        Payment payment = Payment.builder()
                .orderId(order.getId()).gateway("UPI")
                .amount(new BigDecimal("500.00")).currency("INR")
                .paymentStatus("PAID").build();
        paymentRepository.save(payment);

        // Initiate refund
        Refund refund = orderService.initiateRefund(
                order.getId(), USER_MGR, "ROLE_MANAGER", REST_ID, "Customer request");

        assertEquals("REQUESTED", refund.getRefundStatus());
        assertEquals(order.getId(), refund.getOrderId());
        assertEquals(new BigDecimal("500.00"), refund.getAmount());

        // Verify order status changed
        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("REFUND_PENDING", updated.getPaymentStatus());

        // Verify audit trail was recorded
        List<AuditTrail> audits = auditTrailRepository
                .findByRestaurantIdAndActionOrderByRecordedAtDesc(REST_ID, "REFUND_INITIATED");
        assertFalse(audits.isEmpty(), "Audit trail should have REFUND_INITIATED entry");
        assertEquals(order.getId(), audits.get(0).getEntityId());

        // Complete refund
        Refund completed = orderService.completeRefund(refund.getId(), "stripe_re_abc123");
        assertEquals("COMPLETED", completed.getRefundStatus());
        assertEquals("stripe_re_abc123", completed.getProviderRefundId());
        assertNotNull(completed.getCompletedAt());

        // Verify order is now REFUNDED
        Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("REFUNDED", finalOrder.getPaymentStatus());

        // Verify payment is REFUNDED
        Payment finalPayment = paymentRepository.findById(payment.getTransactionId()).orElseThrow();
        assertEquals("REFUNDED", finalPayment.getPaymentStatus());

        // Verify audit trail for completion
        List<AuditTrail> completionAudits = auditTrailRepository
                .findByRestaurantIdAndActionOrderByRecordedAtDesc(REST_ID, "REFUND_COMPLETED");
        assertFalse(completionAudits.isEmpty());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Refund: idempotent — duplicate initiation returns existing refund")
    void refundIdempotent() {
        Order order = createOrder("COMPLETED", "PAID", "STRIPE");

        // First initiation
        Refund first = orderService.initiateRefund(
                order.getId(), USER_MGR, "ROLE_MANAGER", REST_ID, "Reason 1");
        assertEquals("REQUESTED", first.getRefundStatus());

        // Second initiation should return same refund
        Refund second = orderService.initiateRefund(
                order.getId(), USER_MGR, "ROLE_MANAGER", REST_ID, "Reason 2");
        assertEquals(first.getId(), second.getId());

        // Only one refund record should exist
        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertEquals(1, refunds.size(), "Should not create duplicate refund");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Refund: rejects non-PAID order")
    void refundRejectsNonPaid() {
        Order order = createOrder("NEW", "PENDING", "CASH");

        assertThrows(IllegalArgumentException.class,
                () -> orderService.initiateRefund(
                        order.getId(), USER_MGR, "ROLE_MANAGER", REST_ID, "reason"));

        // No refund should be created
        assertTrue(refundRepository.findByOrderId(order.getId()).isEmpty());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Refund: rejects customer role")
    void refundRejectsCustomer() {
        Order order = createOrder("COMPLETED", "PAID", "UPI");

        assertThrows(SecurityException.class,
                () -> orderService.initiateRefund(
                        order.getId(), USER_CUST, "ROLE_CUSTOMER", REST_ID, "reason"));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Refund: cross-tenant access rejected")
    void refundRejectsCrossTenant() {
        // Create order in a different restaurant
        String otherRestId = "REST_OTHER";
        restaurantRepository.save(Restaurant.builder()
                .id(otherRestId).name("Other Restaurant").status("ACTIVE").currency("INR").build());

        Order order = Order.builder()
                .restaurantId(otherRestId).customerName("Other Customer")
                .totalAmount(new BigDecimal("300.00")).orderType("PICKUP")
                .orderStatus("COMPLETED").paymentStatus("PAID").paymentMethod("UPI")
                .build();
        Order saved = orderRepository.save(order);

        assertThrows(SecurityException.class,
                () -> orderService.initiateRefund(
                        saved.getId(), USER_MGR, "ROLE_MANAGER", REST_ID, "reason"));
    }

    // ─── AUDIT TRAIL INTEGRATION TESTS ────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Audit: record and query by entity")
    void auditRecordAndQueryByEntity() {
        auditService.record(REST_ID, USER_MGR, "ROLE_MANAGER",
                "MENU_ITEM_CREATED", "MENU_ITEM", "MI_001",
                "{\"title\":\"Biryani\",\"price\":380}", "New dish added");

        auditService.record(REST_ID, USER_MGR, "ROLE_MANAGER",
                "MENU_ITEM_UPDATED", "MENU_ITEM", "MI_001",
                "{\"price\":420}", "Price increase");

        List<AuditTrail> audits = auditTrailRepository
                .findByEntityTypeAndEntityIdOrderByRecordedAtDesc("MENU_ITEM", "MI_001");

        assertEquals(2, audits.size());
        // Query returns ordered by recordedAt DESC — most recent first
        assertEquals("MENU_ITEM_UPDATED", audits.get(0).getAction());
        assertEquals("MENU_ITEM_CREATED", audits.get(1).getAction());
        assertEquals(REST_ID, audits.get(0).getRestaurantId());
        assertEquals(USER_MGR, audits.get(0).getActorUserId());
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Audit: record and query by action")
    void auditRecordAndQueryByAction() {
        auditService.record(REST_ID, USER_CHEF, "ROLE_CHEF",
                "ORDER_STATUS_CHANGED", "ORDER", "ORD_A",
                "NEW → PREPARING", null);
        auditService.record(REST_ID, USER_MGR, "ROLE_MANAGER",
                "ORDER_CANCELLED", "ORDER", "ORD_B",
                "Cancelled by manager", "Customer request");

        List<AuditTrail> cancelled = auditTrailRepository
                .findByRestaurantIdAndActionOrderByRecordedAtDesc(REST_ID, "ORDER_CANCELLED");

        assertEquals(1, cancelled.size());
        assertEquals("ORD_B", cancelled.get(0).getEntityId());
        assertEquals("ROLE_MANAGER", cancelled.get(0).getActorRole());
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Audit: recent query respects limit")
    void auditRecentRespectsLimit() {
        for (int i = 0; i < 10; i++) {
            auditService.record(REST_ID, USER_MGR, "ROLE_MANAGER",
                    "TEST_ACTION_" + i, "ORDER", "ORD_" + i,
                    "payload", null);
        }

        List<AuditTrail> recent = auditTrailRepository.findRecent(REST_ID, 5);
        assertEquals(5, recent.size());
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Audit: tenant isolation — different restaurant's audits not visible")
    void auditTenantIsolation() {
        String otherRest = "REST_AUDIT_OTHER";
        restaurantRepository.save(Restaurant.builder()
                .id(otherRest).name("Other Audit Restaurant").status("ACTIVE").currency("INR").build());

        auditService.record(otherRest, "USR_X", "ROLE_MANAGER",
                "SECRET_ACTION", "ORDER", "ORD_X", "secret", null);

        List<AuditTrail> myAudits = auditTrailRepository
                .findByRestaurantIdAndActionOrderByRecordedAtDesc(REST_ID, "SECRET_ACTION");
        assertTrue(myAudits.isEmpty(), "Should not see other restaurant's audits");

        List<AuditTrail> otherAudits = auditTrailRepository
                .findByRestaurantIdAndActionOrderByRecordedAtDesc(otherRest, "SECRET_ACTION");
        assertEquals(1, otherAudits.size());
    }

    // ─── ORDER CANCELLATION INTEGRATION TESTS ─────────────────────

    @Test
    @org.junit.jupiter.api.Order(20)
    @DisplayName("Cancellation: manager cancels NEW order → CANCELLED + audit + history")
    void cancelNewOrderWithAudit() {
        Order order = createOrder("NEW", "PENDING", "CASH");

        Order cancelled = orderService.updateStatus(
                order.getId(), REST_ID, "CANCELLED", USER_MGR, "ROLE_MANAGER");

        assertEquals("CANCELLED", cancelled.getOrderStatus());
        assertNotNull(cancelled.getCancelledBy());
        assertNotNull(cancelled.getCancelledAt());

        // Verify order status history was recorded
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(order.getId());
        assertFalse(history.isEmpty());
        assertEquals("NEW", history.get(0).getFromStatus());
        assertEquals("CANCELLED", history.get(0).getToStatus());
        assertEquals(USER_MGR, history.get(0).getChangedBy());

        // Verify audit trail
        List<AuditTrail> audits = auditTrailRepository
                .findByRestaurantIdAndActionOrderByRecordedAtDesc(REST_ID, "ORDER_CANCELLED");
        assertFalse(audits.isEmpty());
        assertEquals(order.getId(), audits.get(0).getEntityId());
    }

    @Test
    @org.junit.jupiter.api.Order(21)
    @DisplayName("Cancellation: customer can cancel NEW order")
    void customerCancelsNewOrder() {
        Order order = createOrder("NEW", "PENDING", "CASH");
        order.setUserId(USER_CUST);
        order = orderRepository.save(order);

        // Customer cancels via updateStatus (simulating what the cancel endpoint does)
        Order cancelled = orderService.updateStatus(
                order.getId(), REST_ID, "CANCELLED", USER_CUST, "ROLE_CUSTOMER");

        assertEquals("CANCELLED", cancelled.getOrderStatus());
    }

    @Test
    @org.junit.jupiter.api.Order(22)
    @DisplayName("Cancellation: invalid transition rejected by state machine")
    void cancelCompletedOrderRejected() {
        Order order = createOrder("COMPLETED", "PAID", "UPI");

        assertThrows(com.savorystay.config.OrderStateException.class,
                () -> orderService.updateStatus(
                        order.getId(), REST_ID, "CANCELLED", USER_MGR, "ROLE_MANAGER"));

        // Order should remain COMPLETED
        Order still = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("COMPLETED", still.getOrderStatus());
    }

    @Test
    @org.junit.jupiter.api.Order(23)
    @DisplayName("Cancellation: chef cannot decline order")
    void chefCannotDeclineOrder() {
        Order order = createOrder("NEW", "PENDING", "CASH");

        assertThrows(SecurityException.class,
                () -> orderService.updateStatus(
                        order.getId(), REST_ID, "DECLINED", USER_CHEF, "ROLE_CHEF"));
    }

    @Test
    @org.junit.jupiter.api.Order(24)
    @DisplayName("Cancellation: terminal state protection — cannot transition from CANCELLED")
    void terminalStateProtection() {
        Order order = createOrder("CANCELLED", "PENDING", "CASH");

        assertThrows(com.savorystay.config.OrderStateException.class,
                () -> orderService.updateStatus(
                        order.getId(), REST_ID, "PREPARING", USER_MGR, "ROLE_MANAGER"));
    }

    // ─── INVENTORY INTEGRATION TESTS ──────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(30)
    @DisplayName("Inventory: ingredient deduction and release on cancellation")
    void inventoryDeductionAndRelease() {
        // Create ingredient with stock
        Ingredient rice = createIngredient("Rice", "kg", new BigDecimal("10.000"));
        String originalStockId = rice.getId();

        // Create menu item
        MenuItem menuItem = MenuItem.builder()
                .restaurantId(REST_ID).title("Biryani")
                .price(new BigDecimal("380")).category("Mains")
                .status("Available").isVeg(false).spiceLevel("Spicy")
                .build();
        menuItem = menuItemRepository.save(menuItem);

        // Link ingredient to menu item
        MenuItemIngredient mii = MenuItemIngredient.builder()
                .menuItemId(menuItem.getId()).ingredientId(rice.getId())
                .restaurantId(REST_ID).name("Rice")
                .quantityPerUnit(new BigDecimal("0.500")).unit("kg")
                .build();
        menuItemIngredientRepository.save(mii);

        // Create order with this menu item
        Order order = createOrder("NEW", "PENDING", "CASH");
        OrderItem orderItem = OrderItem.builder()
                .orderId(order.getId()).menuItemId(menuItem.getId())
                .title("Biryani").quantity(2)
                .unitPrice(new BigDecimal("380"))
                .build();
        orderItemRepository.save(orderItem);

        // Start cooking → deducts inventory
        orderService.updateStatus(order.getId(), REST_ID, "PREPARING", USER_CHEF, "ROLE_CHEF");

        Ingredient afterDeduction = ingredientRepository.findById(originalStockId).orElseThrow();
        // 10 - (0.5 * 2) = 9.0
        assertEquals(0, new BigDecimal("9.000").compareTo(afterDeduction.getStockQuantity()),
                "Stock should be 9 after deducting 2 × 0.5kg");

        // Verify ledger entry
        List<InventoryLedger> ledger = inventoryLedgerRepository
                .findByInventoryIdOrderByRecordedAtAsc(originalStockId);
        assertFalse(ledger.isEmpty());
        assertEquals("ORDER_CONSUMED", ledger.get(0).getReason());
        assertEquals(order.getId(), ledger.get(0).getReferenceId());

        // Cancel order → release inventory
        orderService.updateStatus(order.getId(), REST_ID, "CANCELLED", USER_MGR, "ROLE_MANAGER");

        Ingredient afterRelease = ingredientRepository.findById(originalStockId).orElseThrow();
        assertEquals(0, new BigDecimal("10.000").compareTo(afterRelease.getStockQuantity()),
                "Stock should be restored to 10 after cancellation release");

        // Verify release ledger entry
        List<InventoryLedger> fullLedger = inventoryLedgerRepository
                .findByInventoryIdOrderByRecordedAtAsc(originalStockId);
        assertEquals(2, fullLedger.size());
        assertEquals("CANCELLATION_RELEASE", fullLedger.get(1).getReason());
    }

    // ─── PAYMENT IDEMPOTENCY INTEGRATION TESTS ────────────────────

    @Test
    @org.junit.jupiter.api.Order(40)
    @DisplayName("Payment: confirmPayment idempotent on PAID order")
    void paymentConfirmIdempotent() {
        Order order = createOrder("NEW", "PENDING", "UPI");

        // First confirmation
        Order paid = orderService.confirmPayment(
                order.getId(), USER_CUST, "ROLE_CUSTOMER", REST_ID,
                new BigDecimal("500.00"), "UPI");
        assertEquals("PAID", paid.getPaymentStatus());

        // Second confirmation should be idempotent
        Order stillPaid = orderService.confirmPayment(
                order.getId(), USER_CUST, "ROLE_CUSTOMER", REST_ID,
                new BigDecimal("500.00"), "UPI");
        assertEquals("PAID", stillPaid.getPaymentStatus());

        // Only one payment record
        List<Payment> payments = paymentRepository.findByOrderId(order.getId());
        assertEquals(1, payments.size(), "Should not create duplicate payment");
    }

    @Test
    @org.junit.jupiter.api.Order(41)
    @DisplayName("Payment: CASH order rejection")
    void paymentRejectsCash() {
        Order order = createOrder("NEW", "PENDING", "CASH");

        assertThrows(IllegalArgumentException.class,
                () -> orderService.confirmPayment(
                        order.getId(), USER_CUST, "ROLE_CUSTOMER", REST_ID,
                        new BigDecimal("500.00"), "CASH"));
    }

    @Test
    @org.junit.jupiter.api.Order(42)
    @DisplayName("Payment: amount mismatch rejected")
    void paymentRejectsAmountMismatch() {
        Order order = createOrder("NEW", "PENDING", "UPI");

        assertThrows(IllegalArgumentException.class,
                () -> orderService.confirmPayment(
                        order.getId(), USER_CUST, "ROLE_CUSTOMER", REST_ID,
                        new BigDecimal("300.00"), "UPI"));
    }
}
