package com.savorystay.repository;

import com.savorystay.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status IN ('PUBLISHED', 'FAILED') AND e.createdAt < :cutoff")
    int deleteOldCompletedEvents(LocalDateTime cutoff);

    // ─── P0.24: Row-level locking for safe concurrent polling ─────

    /**
     * Unlock events that were claimed by a now-crashed instance (locked > 30s ago).
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.lockedAt = NULL, e.lockedBy = NULL " +
           "WHERE e.lockedAt IS NOT NULL AND e.lockedAt < :staleThreshold AND e.status = 'PENDING'")
    int unlockStaleEvents(@Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * Claim pending events for this poller instance.
     * Only picks up events that are:
     *  - PENDING
     *  - not locked (lockedAt IS NULL)
     *  - not yet published
     *
     * Uses UPDATE ... LIMIT to atomically claim a batch.
     */
    @Modifying
    @Query(value = "UPDATE outbox_event SET locked_at = :lockedAt, locked_by = :lockedBy " +
           "WHERE id IN (SELECT id FROM (SELECT id FROM outbox_event " +
           "WHERE status = 'PENDING' AND locked_at IS NULL AND published_at IS NULL " +
           "ORDER BY created_at ASC LIMIT 10) AS tmp)", nativeQuery = true)
    int claimPendingEvents(@Param("lockedAt") LocalDateTime lockedAt, @Param("lockedBy") String lockedBy);

    /**
     * After claiming, fetch the claimed events for this instance.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.lockedBy = :instanceId AND e.status = 'PENDING' " +
           "AND e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEvent> findClaimedEvents(@Param("instanceId") String instanceId);
}
