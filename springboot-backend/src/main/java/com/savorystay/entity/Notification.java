package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A notification delivered to a user (customer or restaurant staff).
 * Persisted for history and pushed in real-time over SSE.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    @Column(name = "user_id", length = 64)
    private String userId; // recipient (nullable for restaurant/staff broadcasts)

    @Column(name = "restaurant_id", length = 64)
    private String restaurantId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(length = 30)
    private String type; // ORDER_STATUS, ORDER_READY, NEW_ORDER, STAFF, SYSTEM

    @Column(length = 60)
    private String channel; // APP, or comma list e.g. "APP,SMS,WHATSAPP,EMAIL"

    @Column(name = "is_read")
    private Boolean read;

    // ---------- Real-time delivery targets (reference §7.18) ----------
    @Column(name = "delivery_phone", length = 20)
    private String deliveryPhone; // SMS / WhatsApp recipient

    @Column(name = "delivery_email", length = 100)
    private String deliveryEmail; // Email recipient

    // ---------- Delivery tracking (reference §7.18) ----------
    @Column(length = 20)
    private String status; // PENDING, SENT, DELIVERED, FAILED, READ

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        // UUID-based so notifications dispatched in the same millisecond never collide
        if (id == null) id = "NTF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        if (channel == null) channel = "APP";
        if (read == null) read = false;
        if (status == null) status = "PENDING";
        if (attemptCount == null) attemptCount = 0;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
