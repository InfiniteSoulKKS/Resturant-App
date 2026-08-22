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
import java.util.UUID;

/**
 * Transactional Outbox → Kafka publisher.
 *
 * P0.24: Uses row-level locking (locked_at/locked_by) via repository methods
 * to prevent the same event from being processed by multiple concurrent poll cycles.
 *
 * Each event is published in its OWN transaction so one failing publish rolls
 * back only itself while other events commit independently. This yields
 * at-least-once delivery — no notification is ever lost.
 *
 * Consumers MUST be idempotent (P0.23).
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

    private static final int MAX_RETRIES = 3;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    @Scheduled(fixedDelay = 3_000, initialDelay = 5_000)
    public void pollAndPublish() {
        if (!kafkaEnabled) return;

        // P0.24: Claim pending events with row locking to prevent duplicate processing
        List<OutboxEvent> claimed = claimPendingEvents();
        if (claimed.isEmpty()) return;

        log.info("[OUTBOX] Publishing {} claimed event(s) to Kafka (instance: {})", claimed.size(), instanceId);
        for (OutboxEvent event : claimed) {
            try {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.executeWithoutResult(status -> {
                    publish(event);
                    event.setPublishedAt(LocalDateTime.now());
                    event.setStatus("PUBLISHED");
                    event.setRetryCount(event.getRetryCount() + 1);
                    event.setLockedAt(null);
                    event.setLockedBy(null);
                    outboxEventRepository.save(event);
                });
            } catch (Exception e) {
                int nextRetry = event.getRetryCount() + 1;
                log.error("[OUTBOX] Failed to publish event {} ({}) attempt {}/{}, error: {}",
                        event.getId(), event.getEventType(), nextRetry, MAX_RETRIES, e.getMessage());

                try {
                    TransactionTemplate tx = new TransactionTemplate(transactionManager);
                    tx.executeWithoutResult(status -> {
                        event.setRetryCount(nextRetry);
                        event.setLockedAt(null);
                        event.setLockedBy(null);
                        if (nextRetry >= MAX_RETRIES) {
                            event.setStatus("FAILED");
                            event.setFailedAt(LocalDateTime.now());
                            log.warn("[OUTBOX] Event {} ({}) permanently FAILED after {} retries",
                                    event.getId(), event.getEventType(), nextRetry);
                        }
                        outboxEventRepository.save(event);
                    });
                } catch (Exception saveEx) {
                    log.error("[OUTBOX] Failed to update retry state: {}", saveEx.getMessage());
                }
            }
        }
    }

    /**
     * Atomically claim pending events using repository-level locking.
     * First unlocks stale events from crashed instances, then claims fresh ones.
     */
    private List<OutboxEvent> claimPendingEvents() {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            return tx.execute(status -> {
                // Unlock stale events from crashed instances (locked > 30s ago)
                outboxEventRepository.unlockStaleEvents(LocalDateTime.now().minusSeconds(30));

                // Claim fresh pending events for this instance
                int claimed = outboxEventRepository.claimPendingEvents(LocalDateTime.now(), instanceId);
                if (claimed == 0) return List.of();

                // Fetch the claimed events
                return outboxEventRepository.findClaimedEvents(instanceId);
            });
        } catch (Exception e) {
            log.warn("[OUTBOX] Failed to claim events: {}", e.getMessage());
            return List.of();
        }
    }

    private void publish(OutboxEvent event) {
        String topic = eventPublisher.topicFor(event.getEventType());
        if (topic == null) {
            if ("inventory.stock.decremented".equals(event.getEventType())
                    || "inventory.stock.low".equals(event.getEventType())) {
                log.debug("[OUTBOX] Acknowledging {} for aggregate {} without Kafka",
                        event.getEventType(), event.getAggregateId());
                return;
            }
            log.warn("[OUTBOX] Unknown event type, no Kafka topic: {}", event.getEventType());
            return;
        }
        eventPublisher.publish(topic, event);
    }
}
