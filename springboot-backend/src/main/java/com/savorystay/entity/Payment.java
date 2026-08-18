package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(nullable = false, length = 30)
    private String gateway; // STRIPE, PAYPAL, UPI, CASH

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency; // USD, EUR, INR

    @Column(name = "payment_status", length = 30)
    private String paymentStatus; // PAID, REFUNDED, FAILED

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "gateway_raw_response", columnDefinition = "TEXT")
    private String gatewayRawResponse;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (transactionId == null) transactionId = IdGenerator.newId("TXN");
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (currency == null) currency = "USD";
        if (paymentStatus == null) paymentStatus = "PAID";
    }
}
