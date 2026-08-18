package com.savorystay.scheduler;

import com.savorystay.entity.OutboxEvent;
import com.savorystay.repository.OutboxEventRepository;
import com.savorystay.service.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transactional Outbox → Kafka publisher.
 *
 * Reads pending events (published_at IS NULL) and publishes each one to its
 * Kafka topic ({@link KafkaEventPublisher#topicFor}). The Kafka consumers then
 * dispatch real-time notifications: Gmail emails, Twilio SMS/WhatsApp and
 * in-app SSE pushes.
 *
 * Each event is published in its OWN transaction so one failing publish rolls
 * back only itself (published_at stays NULL → retried on the next poll) while
 * other events in the batch commit independently. This yields at-least-once
 * delivery — no notification is ever lost.
 *
 * Requires Kafka running locally: {@code docker compose up -d}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelay = 3_000, initialDelay = 5_000)
    public void pollAndPublish() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingEvents();
        if (pending.isEmpty()) return;

        log.info("[OUTBOX] Publishing {} pending event(s) to Kafka", pending.size());
        for (OutboxEvent event : pending) {
            try {
                // Per-event transaction boundary: a failed Kafka publish rolls back
                // this event only and it is retried on the next poll.
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.executeWithoutResult(status -> {
                    publish(event);
                    event.setPublishedAt(LocalDateTime.now());
                    outboxEventRepository.save(event);
                });
            } catch (Exception e) {
                log.error("[OUTBOX] Failed to publish event {} ({}), will retry: {}",
                        event.getId(), event.getEventType(), e.getMessage());
            }
        }
    }

    private void publish(OutboxEvent event) {
        String topic = eventPublisher.topicFor(event.getEventType());
        if (topic == null) {
            if ("inventory.stock.decremented".equals(event.getEventType())) {
                // High-volume operational noise — not fanned out to Kafka, just acknowledged.
                log.debug("[OUTBOX] Acknowledging {} for aggregate {} without Kafka",
                        event.getEventType(), event.getAggregateId());
            } else {
                log.warn("[OUTBOX] Unknown event type, no Kafka topic: {}", event.getEventType());
            }
            return;
        }
        eventPublisher.publish(topic, event);
    }
}
