package com.savorystay.scheduler;

import com.savorystay.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Periodically purges old outbox events that have already been published or
 * permanently failed. Keeps the {@code outbox_event} table from growing
 * unbounded over weeks of operation.
 *
 * Runs once every 6 hours and deletes events older than {@link #RETENTION_DAYS}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxEventRepository;

    /** How long to keep completed/failed outbox events before purging. */
    private static final int RETENTION_DAYS = 7;

    @Scheduled(cron = "0 0 */6 * * *") // every 6 hours
    @Transactional
    public void purgeOldEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);

        int deleted = outboxEventRepository.deleteOldCompletedEvents(cutoff);
        if (deleted > 0) {
            log.info("[OUTBOX CLEANUP] Purged {} outbox events older than {} days (cutoff={})",
                    deleted, RETENTION_DAYS, cutoff);
        }
    }
}
