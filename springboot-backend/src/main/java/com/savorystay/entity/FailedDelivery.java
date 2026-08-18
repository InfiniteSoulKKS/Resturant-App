package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit row for notifications that exhausted all retry attempts and were
 * dead-lettered to a Kafka {@code -dlt} topic. Written by the DLT handlers
 * so failed deliveries (bad emails, provider outages, malformed events)
 * are visible and can be retried manually.
 */
@Entity
@Table(name = "failed_delivery")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class FailedDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The topic the event was originally published to (e.g. savorystay.orders). */
    @Column(name = "source_topic", length = 100)
    private String sourceTopic;

    /** The actual DLT topic that received the message (e.g. savorystay.orders-dlt). */
    @Column(name = "received_topic", length = 100)
    private String receivedTopic;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "aggregate_id", length = 64)
    private String aggregateId;

    /** Raw Kafka envelope JSON — kept verbatim for replay / debugging. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /** Exception message from the last failed delivery attempt. */
    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "failed_at", updatable = false)
    private LocalDateTime failedAt;

    @PrePersist
    protected void onCreate() {
        if (failedAt == null) failedAt = LocalDateTime.now();
    }
}
