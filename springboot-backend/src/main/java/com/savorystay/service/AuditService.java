package com.savorystay.service;

import com.savorystay.entity.AuditTrail;
import com.savorystay.repository.AuditTrailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Reusable audit trail service. Every important business mutation should call
 * one of the record() methods. The audit trail is append-only — never mutated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditTrailRepository auditTrailRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(String restaurantId, String actorUserId, String actorRole,
                       String action, String entityType, String entityId,
                       String oldValue, String newValue, String reason) {
        try {
            AuditTrail entry = AuditTrail.builder()
                    .restaurantId(restaurantId)
                    .actorUserId(actorUserId)
                    .actorRole(actorRole)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .reason(reason)
                    .build();
            auditTrailRepository.save(entry);
        } catch (Exception e) {
            // Audit failure must never block the business operation
            log.warn("Failed to record audit trail for {} on {}: {}", action, entityType, e.getMessage());
        }
    }

    /**
     * Convenience overload: record with only the new value (no old state).
     */
    @Transactional
    public void record(String restaurantId, String actorUserId, String actorRole,
                       String action, String entityType, String entityId,
                       String newValue, String reason) {
        record(restaurantId, actorUserId, actorRole, action, entityType, entityId,
                null, newValue, reason);
    }

    /**
     * Convenience overload: record with a Map payload (auto-serialized to JSON).
     */
    @Transactional
    public void record(String restaurantId, String actorUserId, String actorRole,
                       String action, String entityType, String entityId,
                       Map<String, Object> newValue, String reason) {
        String json;
        try {
            json = objectMapper.writeValueAsString(newValue);
        } catch (Exception e) {
            json = String.valueOf(newValue);
        }
        record(restaurantId, actorUserId, actorRole, action, entityType, entityId,
                null, json, reason);
    }

    public List<AuditTrail> getRecent(String restaurantId, int limit) {
        return auditTrailRepository.findRecent(restaurantId, limit);
    }

    public List<AuditTrail> getByEntity(String entityType, String entityId) {
        return auditTrailRepository.findByEntityTypeAndEntityIdOrderByRecordedAtDesc(entityType, entityId);
    }

    public List<AuditTrail> getByAction(String restaurantId, String action) {
        return auditTrailRepository.findByRestaurantIdAndActionOrderByRecordedAtDesc(restaurantId, action);
    }
}
