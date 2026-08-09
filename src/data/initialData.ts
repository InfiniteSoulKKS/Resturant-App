import { MenuItem, Order, PrepItem, EstimatedRawMaterial } from '../types';

export const INITIAL_MENU_ITEMS: MenuItem[] = [
  {
    id: 'm1',
    title: 'Hyderabadi Dum Biryani',
    description: 'Slow-cooked fragrant basmati rice layered with spiced tender chicken, saffron, and caramelised onions. Served with Mirchi ka Salan & Raita.',
    price: 380,
    category: 'Mains',
    imageUrl: 'https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?auto=format&fit=crop&q=80&w=800',
    status: 'Available',
    tag: 'Chef Special',
    isVeg: false,
    spiceLevel: 'Spicy'
  },
  {
    id: 'm2',
    title: 'Paneer Butter Masala',
    description: 'Cottage cheese cubes simmered in a rich, velvety tomato, butter, and cashew cream gravy with aromatic kasuri methi.',
    price: 290,
    category: 'Mains',
    imageUrl: 'https://images.unsplash.com/photo-1631452180519-c014fe946bc7?auto=format&fit=crop&q=80&w=800',
    status: 'Available',
    tag: 'Bestseller',
    isVeg: true,
    spiceLevel: 'Medium'
  },
  {
    id: 'm3',
    title: 'Amritsari Paneer Tikka',
    description: 'Charcoal-grilled paneer cubes marinated in hung curd, carom seeds (ajwain), and mustard oil. Served with mint chutney.',
    price: 240,
    category: 'Starters',
    imageUrl: 'https://images.unsplash.com/photo-1599487488170-d11ec9c172f0?auto=format&fit=crop&q=80&w=800',
    status: 'Available',
    tag: 'Tandoor',
    isVeg: true,
    spiceLevel: 'Medium'
  },
  {
    id: 'm4',
    title: 'Butter Garlic Naan',
    description: 'Refined flour flatbread baked fresh in clay tandoor oven, brushed with creamy garlic butter and coriander.',
    price: 60,
    category: 'Breads',
    imageUrl: 'https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&q=80&w=800',
    status: 'Available',
    tag: 'Fresh Tandoor',
    isVeg: true,
    spiceLevel: 'Mild'
  },
  {
    id: 'm5',
    title: 'Dal Makhani',
    description: 'Overnight slow-cooked black lentils & red kidney beans enriched with white butter, fresh cream, and delicate spices.',
    price: 260,
    category: 'Mains',
    imageUrl: 'https://images.unsplash.com/photo-1546833999-b9f581a1996d?auto=format&fit=crop&q=80&w=800',
    status: 'Available',
    tag: 'Classic',
    isVeg: true,
    spiceLevel: 'Mild'
  },
  {
    id: 'm6',
    title: 'Gulab Jamun with Rabri',
    description: 'Warm, golden khoya dumplings soaked in cardamom sugar syrup, topped with chilled saffron rabri and pistachio flakes.',
    price: 140,
    category: 'Desserts',
    imageUrl: 'https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&q=80&w=800',
    status: 'Available',
    tag: 'Dessert',
    isVeg: true,
    spiceLevel: 'Mild'
  },
  {
    id: 'm7',
    title: 'Kesar Masala Chai',
    description: 'Traditional Indian spiced milk tea infused with cardamom, ginger, cloves, and Kashmiri saffron threads.',
    price: 60,
    category: 'Beverages',
    imageUrl: 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&q=80&w=800',
    status: 'Available',
    tag: 'Hot Beverage',
    isVeg: true,
    spiceLevel: 'Mild'
  }
];

export const INITIAL_ORDERS: Order[] = [
  {
    id: 'ord_4092',
    orderNumber: '#ORD-4092',
    orderType: 'PICKUP',
    pickupTime: '07:30 PM (Today)',
    timeSlot: '07:30 PM',
    customerName: 'Rahul Sharma',
    customerPhone: '+91 98765 43210',
    customerEmail: 'rahul.sharma@example.com',
    items: [
      { id: 'item1', title: 'Hyderabadi Dum Biryani', price: 380, quantity: 2 },
      { id: 'item2', title: 'Butter Garlic Naan', price: 60, quantity: 2 },
      { id: 'item3', title: 'Gulab Jamun with Rabri', price: 140, quantity: 1 }
    ],
    totalAmount: 1020,
    paymentStatus: 'PAID',
    paymentMethod: 'UPI',
    paymentTransactionId: 'TXN_UPI_9812739182',
    orderStatus: 'NEW',
    createdAt: new Date(Date.now() - 5 * 60000).toISOString(),
    timestamp: Date.now() - 5 * 60000,
    notificationsSent: ['SMS', 'WhatsApp']
  },
  {
    id: 'ord_4091',
    orderNumber: '#ORD-4091',
    orderType: 'DINE_IN',
    tableNumber: 12,
    guests: 4,
    timeSlot: '07:15 PM',
    customerName: 'Ananya Verma',
    customerPhone: '+91 98112 33445',
    customerEmail: 'ananya.v@example.com',
    items: [
      { id: 'item3', title: 'Amritsari Paneer Tikka', price: 240, quantity: 2 },
      { id: 'item4', title: 'Paneer Butter Masala', price: 290, quantity: 1 },
      { id: 'item5', title: 'Dal Makhani', price: 260, quantity: 1 },
      { id: 'item6', title: 'Butter Garlic Naan', price: 60, quantity: 4 }
    ],
    totalAmount: 1270,
    paymentStatus: 'PAID',
    paymentMethod: 'RAZORPAY',
    paymentTransactionId: 'TXN_RZP_7721839121',
    orderStatus: 'PREPARING',
    createdAt: new Date(Date.now() - 20 * 60000).toISOString(),
    timestamp: Date.now() - 20 * 60000,
    notificationsSent: ['SMS', 'Email', 'App Push']
  }
];

export const INITIAL_PREP_ITEMS: PrepItem[] = [
  {
    id: 'prep_1',
    itemName: 'Seared Scallops',
    category: 'Starters',
    requiredCount: 24,
    preppedCount: 12,
    tag: 'Seafood',
    priority: 'Normal',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCqy_UR5Y37yR2gNeyROzvTPtS_H2f7U0iJW0p28qQxxwlc4u42AFLypZ_8jvxanyrIht9bHdp5WSe5I-wD3ykKF32EgXZSdG6_R5uoEmXEm5GwNFlwbTN5DH4CvdIIhYngkECZ9HBkQE7cY1Iut6-Gffu8THoc9j4t93RV86iXYlLSCIx1_J2HKZRwh6QrqA7Njku4rKmhOvR1iqglVYjH6BUzxuNacfP9RC0Lmt6Tr1P_3twz8diw'
  },
  {
    id: 'prep_2',
    itemName: 'Burrata Salad',
    category: 'Starters',
    requiredCount: 18,
    preppedCount: 18,
    tag: 'Dairy/Veg',
    priority: 'Normal',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDuTrWAHv3QXBXy-38WbgAnqYa3GLdTzviUJatbWgJtrrL5KPN-BVMkJNGfprWTEtfIL-OUGmAkmcWuOivwZMGjcJ-dsXTztuB_QaRFagOYThrVNpUxEw3NR6yWvtaljAzau-qpDSBiOdUNA_K7c29NJ1ZyXcbvpvRJV1KB5BHTZWoK-vyPQ--pKWOWUFxZntg5DrnUAbI9hW1MfHoeznj72LK32zBdqV0RhMx2lKybivYMpqLJW9Fy'
  },
  {
    id: 'prep_3',
    itemName: 'Truffle Burger',
    category: 'Mains',
    requiredCount: 45,
    preppedCount: 5,
    tag: 'Priority',
    priority: 'Priority',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuC8euFnR_olUbiEv-iGkkYPEo4qzIkAELQINJP9lrT8xRDEUVLlOHGvYbrngmIyNr2W8QxKyijRUv-fo_8Rpw9vJ6kjCHd2uWG87eTbIN8p3OTqoLzYMc6mhEjMNcYZE4NUTcytucOXID2a92gofq-J8D7CF1qoUa9is0XctcJLBlWuinvPUQ9ShqcS4vB4DuZYtQygtA8GRMktpyXbrh7dt-P8vRItuL39ILJ6U1l7HxXMDklu7HY6'
  },
  {
    id: 'prep_4',
    itemName: 'Mushroom Risotto',
    category: 'Mains',
    requiredCount: 22,
    preppedCount: 15,
    tag: 'Veg',
    priority: 'Normal',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBzIu4VBlHxsDshLfXkReGbBwXVsBvi1pPrZDxzhKPVLdJwmMWmpHGyAl6TxIV5fR2Lyino1yZMR3Q8056PLNR35LTH4QDveL3DLgs99Y4Iqa_ExKPCBMU6j5van61fgjDZ3nG19G9chZ-8mEVb6kItj3946C6nTihGh1jg793CmP8rtF_Q_9gRCWFiFIcndXJTJkaulDmDOXM4JzxVOQE-ggMiQC33KHYD0GtA0PzMLABPFFdwkrMX'
  },
  {
    id: 'prep_5',
    itemName: 'Chocolate Torte',
    category: 'Desserts',
    requiredCount: 12,
    preppedCount: 12,
    tag: 'Pastry',
    priority: 'Normal',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCvk_xSOCLIH36DGc2p9PsYY5pwMee2jS4rf_n2GX3ijnpUNew9LLXG7q9Wvqrlq-GyC4Dg3FVeIp5M7_frtEROJC90HLyjZus1X5-MydW7Wc_G5JM1u3EcrQHnOgskbkBaIrnRxByHyeq_CuFAL2knBkFpS5a4QLkdz0sgt69pqtGyI9LtCPgNgZbjcg9hFij0xx_peXwEVtD18WXczneexvIqCI4VhPgu7smiYoydGl_gAqCquEUJ'
  }
];

export const INITIAL_RAW_MATERIALS: EstimatedRawMaterial[] = [
  { name: 'Basmati Rice', amount: '15kg' },
  { name: 'Chicken/Mutton', amount: '20kg' },
  { name: 'Onions', amount: '8kg' },
  { name: 'Saffron/Spices', amount: '200g' }
];

export const SPRING_SECURITY_CONFIG_CODE = `package com.savorystay.config;

import com.savorystay.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configure(http))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/api/v1/health", "/api/v1/payments/webhook").permitAll()
                .requestMatchers("/api/v1/menu/manage/**").hasAnyRole("CHEF", "ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength 12 BCrypt Salt Rounds
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}`;

export const PAYMENT_GATEWAY_SERVICE_CODE = `package com.savorystay.service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.orders.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentGatewayService {

    @Value("\${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("\${paypal.client.id}")
    private String paypalClientId;

    @Value("\${paypal.client.secret}")
    private String paypalClientSecret;

    public PaymentGatewayService() {
        Stripe.apiKey = stripeSecretKey;
    }

    // Stripe Payment Intent Creation
    public String createStripePaymentIntent(BigDecimal amount, String currency) throws Exception {
        long amountInCents = amount.multiply(new BigDecimal("100")).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountInCents)
            .setCurrency(currency.toLowerCase())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
            )
            .build();

        PaymentIntent intent = PaymentIntent.create(params);
        return intent.getClientSecret();
    }

    // PayPal Express Checkout Order Creation
    public String createPayPalOrder(BigDecimal amount, String currency) throws Exception {
        PayPalEnvironment environment = new PayPalEnvironment.Sandbox(paypalClientId, paypalClientSecret);
        PayPalHttpClient client = new PayPalHttpClient(environment);

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");

        List<PurchaseUnitRequest> purchaseUnits = new ArrayList<>();
        PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest()
            .amountWithBreakdown(new AmountWithBreakdown().currencyCode(currency).value(amount.toString()));
        purchaseUnits.add(purchaseUnitRequest);
        orderRequest.purchaseUnits(purchaseUnits);

        OrdersCreateRequest request = new OrdersCreateRequest().requestBody(orderRequest);
        com.paypal.orders.Order order = client.execute(request).result();
        return order.id();
    }

    // Webhook Signature Verification
    public boolean verifyStripeWebhook(String payload, String sigHeader, String endpointSecret) {
        try {
            com.stripe.net.Webhook.constructEvent(payload, sigHeader, endpointSecret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}`;

export const SPRING_BOOT_CONTROLLER_CODE = `package com.savorystay.controller;

import com.savorystay.model.Order;
import com.savorystay.model.PaymentRequest;
import com.savorystay.model.PaymentResponse;
import com.savorystay.service.OrderService;
import com.savorystay.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class CulinaryOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public CulinaryOrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/prebook")
    public ResponseEntity<Order> createPreBookOrder(@RequestBody Order orderRequest) {
        Order createdOrder = orderService.createPreBooking(orderRequest);
        return ResponseEntity.ok(createdOrder);
    }

    @PostMapping("/payments/process-realtime")
    public ResponseEntity<PaymentResponse> processRealtimePayment(@RequestBody PaymentRequest paymentRequest) {
        PaymentResponse response = paymentService.processRealtimePayment(paymentRequest);
        if ("PAID".equals(response.getPaymentStatus())) {
            orderService.updatePaymentStatus(paymentRequest.getOrderId(), "PAID", response.getTransactionId());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/today")
    public ResponseEntity<List<Order>> getTodayOrders() {
        return ResponseEntity.ok(orderService.findOrdersForToday());
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable("id") String id, @RequestParam("status") String status) {
        Order updated = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}`;

export const MYSQL_SCHEMA_SQL = `-- MySQL DDL Schema for SavoryStay Culinary Operations System

-- Users & Authentication Table (Spring Security)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, -- Encoded via BCryptPasswordEncoder
    role VARCHAR(30) DEFAULT 'ROLE_CUSTOMER',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS menu_items (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    image_url VARCHAR(1024),
    status VARCHAR(20) DEFAULT 'Available',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(64) PRIMARY KEY,
    order_number VARCHAR(20) NOT NULL UNIQUE,
    table_number INT NOT NULL,
    guests INT NOT NULL,
    time_slot VARCHAR(20) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    user_id VARCHAR(64),
    total_amount DECIMAL(10, 2) NOT NULL,
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    payment_method VARCHAR(50),
    payment_transaction_id VARCHAR(100),
    order_status VARCHAR(20) DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS payments (
    transaction_id VARCHAR(100) PRIMARY KEY,
    order_id VARCHAR(64),
    gateway VARCHAR(30) NOT NULL, -- STRIPE, PAYPAL, UPI
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    payment_status VARCHAR(30) DEFAULT 'PAID',
    card_last4 VARCHAR(4),
    client_secret VARCHAR(255),
    gateway_raw_response JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64),
    menu_item_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id)
);

CREATE TABLE IF NOT EXISTS prep_summary (
    id VARCHAR(64) PRIMARY KEY,
    item_name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    required_count INT DEFAULT 0,
    prepped_count INT DEFAULT 0,
    priority VARCHAR(20) DEFAULT 'Normal'
);`;

export const POSTGRESQL_SCHEMA_SQL = MYSQL_SCHEMA_SQL;
