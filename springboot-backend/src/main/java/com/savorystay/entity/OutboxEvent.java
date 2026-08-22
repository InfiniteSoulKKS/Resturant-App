package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Transactional Outbox — every domain event is written here in the same
 * DB transaction as the business data change.
 * A scheduled poller (OutboxPoller) reads unpublished rows and dispatches
 * them to downstream services (Notification, Inventory, etc.), then marks
 * them as published.
 *
 * Corresponds to the reference: outbox_event (§7.15).
 */
@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", length = 64)
    private String aggregateId; // e.g. order_id, ingredient_id

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // order.created, order.status.changed, inventory.stock.decremented, etc.

    @Column(columnDefinition = "TEXT")
    private String payload; // JSON

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** How many times the OutboxPoller has tried (and failed) to publish this event. */
    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** PENDING → FAILED or PUBLISHED. Once FAILED the poller stops retrying. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** P0.24: Row lock timestamp — prevents duplicate processing by concurrent pollers. */
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    /** P0.24: Which poller instance holds the lock. */
    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    /** Timestamp when the event was permanently abandoned (exceeded max retries). */
    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}