package com.savorystay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.entity.FailedDelivery;
import com.savorystay.repository.FailedDeliveryRepository;
import com.savorystay.service.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared {@code @DltHandler} logic: every Kafka consumer funnels exhausted /
 * dead-lettered messages here, which logs them prominently and persists an
 * audit row in {@code failed_delivery} (event type, aggregate, raw payload,
 * error) so failed notification deliveries can be reviewed and replayed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DltRecorder {

    private final FailedDeliveryRepository failedDeliveryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Record a dead-lettered message.
     *
     * @param sourceTopic    the topic the event was originally published to
     * @param receivedTopic  the DLT topic that actually received it
     * @param message        raw envelope JSON (best-effort parsed for the audit row)
     * @param error          exception message from the final failed attempt
     */
    public void record(String sourceTopic, String receivedTopic, String message, String error) {
        String eventType = null;
        String aggregateId = null;
        try {
            KafkaEventPublisher.EventEnvelope envelope =
                    objectMapper.readValue(message, KafkaEventPublisher.EventEnvelope.class);
            eventType = envelope.eventType();
            aggregateId = envelope.aggregateId();
        } catch (Exception ignored) {
            // Malformed envelopes are exactly the kind of poison message the DLT is for —
            // store what we have; the raw payload is preserved either way.
        }

        log.error("[KAFKA DLT] {} on {} → {}: {} (aggregate={})",
                eventType != null ? eventType : "unknown-event",
                sourceTopic, receivedTopic, error != null ? error : "no exception detail", aggregateId);

        try {
            failedDeliveryRepository.save(FailedDelivery.builder()
                    .sourceTopic(sourceTopic)
                    .receivedTopic(receivedTopic)
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(message)
                    .error(error)
                    .build());
        } catch (Exception e) {
            log.error("[KAFKA DLT] Failed to persist dead-letter audit row: {}", e.getMessage());
        }
    }
}
