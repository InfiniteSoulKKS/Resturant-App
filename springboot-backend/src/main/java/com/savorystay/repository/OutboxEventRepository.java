package com.savorystay.repository;

import com.savorystay.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    List<OutboxEvent> findByEventTypeAndAggregateIdAndPublishedAtIsNull(String eventType, String aggregateId);

    long countByPublishedAtIsNull();
}