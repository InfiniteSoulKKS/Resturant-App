package com.savorystay.repository;

import com.savorystay.entity.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {
    List<AuditTrail> findByRestaurantIdOrderByRecordedAtDesc(String restaurantId);
    List<AuditTrail> findByEntityTypeAndEntityIdOrderByRecordedAtDesc(String entityType, String entityId);
    List<AuditTrail> findByRestaurantIdAndActionOrderByRecordedAtDesc(String restaurantId, String action);

    @Query("SELECT a FROM AuditTrail a WHERE a.restaurantId = :restaurantId " +
           "ORDER BY a.recordedAt DESC LIMIT :limit")
    List<AuditTrail> findRecent(@Param("restaurantId") String restaurantId, @Param("limit") int limit);
}
