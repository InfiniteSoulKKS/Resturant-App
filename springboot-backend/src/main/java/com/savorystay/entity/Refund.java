package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund record. One row per refund attempt.
 * The refund lifecycle is: REQUESTED → PROCESSING → COMPLETED / FAILED.
 * A REFUND_PENDING status on Payment means a Refund row exists in REQUESTED state.
 *
 * Structured so PARTIAL_REFUND can be added later without redesign.
 */
@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    private String id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId; // transaction_id from payments table

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency; // INR

    @Column(name = "refund_status", length = 30, nullable = false)
    private String refundStatus; // REQUESTED, PROCESSING, COMPLETED, FAILED

    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId; // Stripe/PayPal refund ID

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "initiated_by", length = 64)
    private String initiatedBy; // user_id or SYSTEM

    @Column(name = "restaurant_id", length = 64)
    private String restaurantId;

    @Column(name = "gateway", length = 30)
    private String gateway;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = IdGenerator.newId("REF");
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
        if (refundStatus == null) refundStatus = "REQUESTED";
    }
}
