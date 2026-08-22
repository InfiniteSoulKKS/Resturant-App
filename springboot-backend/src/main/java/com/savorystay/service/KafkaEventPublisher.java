package com.savorystay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.config.KafkaTopicConfig;
import com.savorystay.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Bridges the transactional outbox to Kafka.
 *
 * {@link com.savorystay.scheduler.OutboxPoller} calls {@link #publish} inside
 * the per-event database transaction. The Kafka {@code send} is awaited with a
 * short timeout so a failed publish throws → the transaction rolls back →
 * {@code published_at} stays NULL → the event is retried on the next poll.
 * This gives at-least-once delivery with no message loss (duplicates are
 * possible only after a commit + lost ack, which the idempotent consumers
 * tolerate).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled;

    /** Wait for the Kafka broker acknowledgement (broker down → quick failure, not a hung thread). */
    private static final long SEND_TIMEOUT_SECONDS = 5;

    /** Publish an outbox event to its topic. Throws on failure so the caller can roll back. */
    public void publish(String topic, OutboxEvent event) {
        if (!kafkaEnabled) {
            log.debug("[KAFKA] Kafka disabled — skipping publish of {} to {}", event.getEventType(), topic);
            return;
        }
        try {
            Map<String, Object> payload = event.getPayload() != null
                    ? objectMapper.readValue(event.getPayload(), new TypeReference<>() {})
                    : Map.of();
            EventEnvelope envelope = new EventEnvelope(
                    UUID.randomUUID().toString(),
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getCreatedAt() != null ? event.getCreatedAt().toString() : LocalDateTime.now().toString(),
                    payload != null ? payload : Map.of());
            String json = objectMapper.writeValueAsString(envelope);

            // aggregateId as the record key keeps events for one aggregate ordered
            kafkaTemplate.send(topic, event.getAggregateId(), json)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("[KAFKA] Published {} (aggregate={}) to {}", event.getEventType(), event.getAggregateId(), topic);
        } catch (Exception e) {
            throw new RuntimeException("Kafka publish failed for " + event.getEventType() + ": " + e.getMessage(), e);
        }
    }

    /** Resolve the topic for an outbox event type (null when the event is not published to Kafka). */
    public String topicFor(String eventType) {
        return switch (eventType) {
            case "order.created", "order.status.changed" -> KafkaTopicConfig.ORDERS_TOPIC;
            case "otp.generated" -> KafkaTopicConfig.OTP_TOPIC;
            case "inventory.stock.low" -> KafkaTopicConfig.INVENTORY_TOPIC;
            case "payment.confirmed" -> KafkaTopicConfig.PAYMENTS_TOPIC;
            default -> null;
        };
    }

    /** Wire envelope written by the publisher and read by the consumers. */
    public record EventEnvelope(String eventId, String eventType, String aggregateId,
                                String occurredAt, Map<String, Object> payload) {
    }
}
