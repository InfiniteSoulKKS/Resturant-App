package com.savorystay.repository;

import com.savorystay.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL AND e.status <> 'FAILED' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    List<OutboxEvent> findByEventTypeAndAggregateIdAndPublishedAtIsNull(String eventType, String aggregateId);

    long countByPublishedAtIsNull();

    /**
     * Finds published or failed events older than the given cutoff.
     * Used by OutboxCleanupScheduler to purge stale outbox rows.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status IN ('PUBLISHED', 'FAILED') AND e.createdAt < :cutoff")
    List<OutboxEvent> findOldCompletedEvents(LocalDateTime cutoff);

    /**
     * Bulk-deletes published or failed events older than the given cutoff.
     * Returns the number of rows deleted.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status IN ('PUBLISHED', 'FAILED') AND e.createdAt < :cutoff")
    int deleteOldCompletedEvents(LocalDateTime cutoff);
}