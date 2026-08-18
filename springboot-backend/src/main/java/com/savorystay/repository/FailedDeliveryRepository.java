package com.savorystay.repository;

import com.savorystay.entity.FailedDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persists dead-lettered notification deliveries for auditing and manual
 * replay. Populated by the Kafka {@code @DltHandler} consumers.
 */
public interface FailedDeliveryRepository extends JpaRepository<FailedDelivery, Long> {

    List<FailedDelivery> findByEventTypeOrderByFailedAtDesc(String eventType);
}
