package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private String id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(name = "table_number", nullable = false)
    private Integer tableNumber;

    @Column(nullable = false)
    private Integer guests;

    @Column(name = "time_slot", nullable = false, length = 20)
    private String timeSlot;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus; // PENDING, PAID, FAILED

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // STRIPE, PAYPAL, UPI, CASH

    @Column(name = "order_status", length = 20)
    private String orderStatus; // NEW, IN_PREPARATION, READY, SERVED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = "ORD_" + System.currentTimeMillis();
        if (orderNumber == null) orderNumber = "#ORD-" + (1000 + (int)(Math.random() * 9000));
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (paymentStatus == null) paymentStatus = "PENDING";
        if (orderStatus == null) orderStatus = "NEW";
    }
}
