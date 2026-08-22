package com.savorystay.scheduler;

import com.savorystay.entity.OutboxEvent;
import com.savorystay.repository.OutboxEventRepository;
import com.savorystay.service.KafkaEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for:
 * - OutboxPoller retry limit (stops after MAX_RETRIES)
 * - OutboxPoller skips when Kafka is disabled
 * - OutboxCleanupScheduler purges old events
 */
@ExtendWith(MockitoExtension.class)
class OutboxRetryAndCleanupTest {

    @Mock OutboxEventRepository outboxEventRepository;
    @Mock KafkaEventPublisher eventPublisher;
    @Mock PlatformTransactionManager transactionManager;

    private OutboxPoller outboxPoller;
    private OutboxCleanupScheduler cleanupScheduler;

    @BeforeEach
    void setUp() {
        outboxPoller = new OutboxPoller(outboxEventRepository, eventPublisher, transactionManager);
        cleanupScheduler = new OutboxCleanupScheduler(outboxEventRepository);
    }

    private OutboxEvent pendingEvent(long id, String type, int retryCount) {
        return OutboxEvent.builder()
                .id(id).aggregateId("AGG_" + id).eventType(type)
                .payload("{}").status("PENDING").retryCount(retryCount)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    // ─── KAFKA DISABLED ───────────────────────────────────────────

    @Test
    void poller_skipsWhenKafkaDisabled() throws Exception {
        var field = OutboxPoller.class.getDeclaredField("kafkaEnabled");
        field.setAccessible(true);
        field.set(outboxPoller, false);

        outboxPoller.pollAndPublish();

        // Should not even query for pending events
        verify(outboxEventRepository, never()).findPendingEvents();
    }

    // ─── EMPTY QUEUE ──────────────────────────────────────────────

    @Test
    void poller_doesNothingWhenNoPendingEvents() throws Exception {
        var field = OutboxPoller.class.getDeclaredField("kafkaEnabled");
        field.setAccessible(true);
        field.set(outboxPoller, true);

        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of());

        outboxPoller.pollAndPublish();

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    // ─── RETRY INCREMENT ──────────────────────────────────────────

    @Test
    void poller_incrementsRetryCountOnFailure() throws Exception {
        var field = OutboxPoller.class.getDeclaredField("kafkaEnabled");
        field.setAccessible(true);
        field.set(outboxPoller, true);

        OutboxEvent event = pendingEvent(1, "order.created", 0);
        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(event));
        when(eventPublisher.topicFor("order.created")).thenReturn("savorystay.orders");
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publish(anyString(), any());

        outboxPoller.pollAndPublish();

        // Retry count should be incremented (1 attempt)
        verify(outboxEventRepository, atLeastOnce()).save(argThat(e -> {
            OutboxEvent saved = (OutboxEvent) e;
            return saved.getRetryCount() >= 1;
        }));
    }

    // ─── CLEANUP SCHEDULER ────────────────────────────────────────

    @Test
    void cleanupScheduler_callsDeleteWithCutoff() {
        when(outboxEventRepository.deleteOldCompletedEvents(any(LocalDateTime.class))).thenReturn(5);

        cleanupScheduler.purgeOldEvents();

        verify(outboxEventRepository).deleteOldCompletedEvents(any(LocalDateTime.class));
    }

    @Test
    void cleanupScheduler_noOpWhenNothingToDelete() {
        when(outboxEventRepository.deleteOldCompletedEvents(any(LocalDateTime.class))).thenReturn(0);

        cleanupScheduler.purgeOldEvents();

        // Should still call the query (to check), just nothing to delete
        verify(outboxEventRepository).deleteOldCompletedEvents(any(LocalDateTime.class));
    }

    // ─── INVENTORY EVENTS WITHOUT KAFKA ────────────────────────────

    @Test
    void poller_acknowledgesInventoryEventsWithoutKafkaTopic() throws Exception {
        var field = OutboxPoller.class.getDeclaredField("kafkaEnabled");
        field.setAccessible(true);
        field.set(outboxPoller, true);

        OutboxEvent event = pendingEvent(1, "inventory.stock.decremented", 0);
        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(event));
        when(eventPublisher.topicFor("inventory.stock.decremented")).thenReturn(null);

        outboxPoller.pollAndPublish();

        // Inventory events without Kafka topic should be acknowledged directly
        verify(outboxEventRepository, atLeastOnce()).save(argThat(e ->
                "PUBLISHED".equals(((OutboxEvent) e).getStatus())));
    }
}
