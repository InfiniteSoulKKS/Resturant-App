package com.savorystay.scheduler;

import com.savorystay.entity.OutboxEvent;
import com.savorystay.repository.OutboxEventRepository;
import com.savorystay.service.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transactional Outbox → Kafka publisher.
 *
 * Reads pending events (published_at IS NULL, status ≠ FAILED) and publishes
 * each one to its Kafka topic ({@link KafkaEventPublisher#topicFor}). The Kafka
 * consumers then dispatch real-time notifications: Gmail emails, Twilio
 * SMS/WhatsApp and in-app SSE pushes.
 *
 * Each event is published in its OWN transaction so one failing publish rolls
 * back only itself (published_at stays NULL → retried on the next poll) while
 * other events in the batch commit independently. This yields at-least-once
 * delivery — no notification is ever lost.
 *
 * <b>Retry safety:</b> After {@link #MAX_RETRIES} consecutive failures an event
 * is marked {@code status=FAILED} so the poller skips it permanently. This
 * prevents infinite retry loops when Kafka is down.
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

    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled;

    /** Stop retrying after this many consecutive failures (3 × 3s = 9s back-off). */
    private static final int MAX_RETRIES = 3;

    @Scheduled(fixedDelay = 3_000, initialDelay = 5_000)
    public void pollAndPublish() {
        if (!kafkaEnabled) return;

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
                    event.setStatus("PUBLISHED");
                    event.setRetryCount(event.getRetryCount() + 1);
                    outboxEventRepository.save(event);
                });
            } catch (Exception e) {
                int nextRetry = event.getRetryCount() + 1;
                log.error("[OUTBOX] Failed to publish event {} ({}) attempt {}/{}, error: {}",
                        event.getId(), event.getEventType(), nextRetry, MAX_RETRIES, e.getMessage());

                // Mark as permanently failed after exhausting retries
                if (nextRetry >= MAX_RETRIES) {
                    try {
                        TransactionTemplate tx = new TransactionTemplate(transactionManager);
                        tx.executeWithoutResult(status -> {
                            event.setRetryCount(nextRetry);
                            event.setStatus("FAILED");
                            event.setFailedAt(LocalDateTime.now());
                            outboxEventRepository.save(event);
                        });
                        log.warn("[OUTBOX] Event {} ({}) permanently FAILED after {} retries —不会再 retry",
                                event.getId(), event.getEventType(), nextRetry);
                    } catch (Exception saveEx) {
                        log.error("[OUTBOX] Failed to mark event as FAILED: {}", saveEx.getMessage());
                    }
                } else {
                    // Increment retry count so next poll knows how far along we are
                    try {
                        TransactionTemplate tx = new TransactionTemplate(transactionManager);
                        tx.executeWithoutResult(status -> {
                            event.setRetryCount(nextRetry);
                            outboxEventRepository.save(event);
                        });
                    } catch (Exception saveEx) {
                        log.error("[OUTBOX] Failed to increment retry count: {}", saveEx.getMessage());
                    }
                }
            }
        }
    }

    private void publish(OutboxEvent event) {
        String topic = eventPublisher.topicFor(event.getEventType());
        if (topic == null) {
            if ("inventory.stock.decremented".equals(event.getEventType())
                    || "inventory.stock.low".equals(event.getEventType())) {
                // High-volume operational noise — not fanned out to Kafka, just acknowledged.
                log.debug("[OUTBOX] Acknowledging {} for aggregate {} without Kafka",
                        event.getEventType(), event.getAggregateId());
                // Mark as published since no Kafka topic is needed
                event.setPublishedAt(LocalDateTime.now());
                event.setStatus("PUBLISHED");
                outboxEventRepository.save(event);
            } else {
                log.warn("[OUTBOX] Unknown event type, no Kafka topic: {}", event.getEventType());
            }
            return;
        }
        eventPublisher.publish(topic, event);
    }
}
