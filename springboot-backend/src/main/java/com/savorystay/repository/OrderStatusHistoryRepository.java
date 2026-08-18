package com.savorystay.repository;

import com.savorystay.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * INSERT-only repository — no UPDATE or DELETE operations.
 * The immutable audit trail is never mutated after creation.
 * Matches the reference: order_status_history is append-only.
 */
@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderIdOrderByChangedAtAsc(String orderId);
}