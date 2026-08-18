package com.savorystay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.entity.AuditTrail;
import com.savorystay.repository.AuditTrailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditService}.
 *
 * Covers:
 *  - Record with all fields
 *  - Record with convenience overloads (no old value, Map payload)
 *  - Query by entity type + entity ID
 *  - Query by action
 *  - Query recent with limit
 *  - Error resilience: save failure doesn't throw
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditTrailRepository auditTrailRepository;
    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditTrailRepository, new ObjectMapper());
    }

    // ─── RECORD ───────────────────────────────────────────────────

    @Test
    void recordSavesAuditEntryWithAllFields() {
        when(auditTrailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record("REST_01", "USR_MGR", "ROLE_MANAGER",
                "ORDER_CANCELLED", "ORDER", "ORD_001",
                "{\"orderNumber\":\"#ORD-1\"}", "{\"orderNumber\":\"#ORD-1\",\"status\":\"CANCELLED\"}",
                "Customer request");

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(auditTrailRepository).save(captor.capture());

        AuditTrail entry = captor.getValue();
        assertEquals("REST_01", entry.getRestaurantId());
        assertEquals("USR_MGR", entry.getActorUserId());
        assertEquals("ROLE_MANAGER", entry.getActorRole());
        assertEquals("ORDER_CANCELLED", entry.getAction());
        assertEquals("ORDER", entry.getEntityType());
        assertEquals("ORD_001", entry.getEntityId());
        assertEquals("{\"orderNumber\":\"#ORD-1\"}", entry.getOldValue());
        assertEquals("{\"orderNumber\":\"#ORD-1\",\"status\":\"CANCELLED\"}", entry.getNewValue());
        assertEquals("Customer request", entry.getReason());
        // recordedAt is set by @PrePersist (JPA lifecycle) — not triggered in unit tests.
        // This is a JPA concern, not an AuditService concern.
    }

    @Test
    void recordConvenienceOverloadWithoutOldValue() {
        when(auditTrailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record("REST_01", "USR_MGR", "ROLE_MANAGER",
                "INGREDIENT_CREATED", "INGREDIENT", "ING_001",
                "{\"name\":\"Chicken\"}", "New ingredient");

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(auditTrailRepository).save(captor.capture());

        AuditTrail entry = captor.getValue();
        assertNull(entry.getOldValue());
        assertEquals("{\"name\":\"Chicken\"}", entry.getNewValue());
        assertEquals("New ingredient", entry.getReason());
    }

    @Test
    void recordConvenienceOverloadWithMapPayload() {
        when(auditTrailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = Map.of(
                "orderId", "ORD_001",
                "amount", 500,
                "reason", "Customer unhappy"
        );
        service.record("REST_01", "USR_MGR", "ROLE_MANAGER",
                "REFUND_INITIATED", "ORDER", "ORD_001",
                payload, "Refund requested");

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(auditTrailRepository).save(captor.capture());

        AuditTrail entry = captor.getValue();
        assertNotNull(entry.getNewValue());
        assertTrue(entry.getNewValue().contains("ORD_001"));
        assertTrue(entry.getNewValue().contains("500"));
    }

    @Test
    void recordWithNullActorFields() {
        when(auditTrailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record("REST_01", null, null,
                "SYSTEM_ACTION", "ORDER", "ORD_001",
                "automated cleanup", null);

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(auditTrailRepository).save(captor.capture());

        AuditTrail entry = captor.getValue();
        assertNull(entry.getActorUserId());
        assertNull(entry.getActorRole());
        assertNull(entry.getReason());
    }

    // ─── ERROR RESILIENCE ─────────────────────────────────────────

    @Test
    void recordDoesNotThrowWhenSaveFails() {
        when(auditTrailRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        // Must not throw — audit failure must never block business operations
        assertDoesNotThrow(() -> service.record("REST_01", "USR_001", "ROLE_MANAGER",
                "TEST_ACTION", "ORDER", "ORD_001", "value", "reason"));
    }

    // ─── QUERIES ──────────────────────────────────────────────────

    @Test
    void getByEntityDelegatesToRepository() {
        AuditTrail a1 = AuditTrail.builder().id(1L).entityType("ORDER").entityId("ORD_001").build();
        AuditTrail a2 = AuditTrail.builder().id(2L).entityType("ORDER").entityId("ORD_001").build();
        when(auditTrailRepository.findByEntityTypeAndEntityIdOrderByRecordedAtDesc("ORDER", "ORD_001"))
                .thenReturn(List.of(a1, a2));

        List<AuditTrail> result = service.getByEntity("ORDER", "ORD_001");

        assertEquals(2, result.size());
        verify(auditTrailRepository).findByEntityTypeAndEntityIdOrderByRecordedAtDesc("ORDER", "ORD_001");
    }

    @Test
    void getByActionDelegatesToRepository() {
        AuditTrail a1 = AuditTrail.builder().id(1L).action("ORDER_CANCELLED").build();
        when(auditTrailRepository.findByRestaurantIdAndActionOrderByRecordedAtDesc("REST_01", "ORDER_CANCELLED"))
                .thenReturn(List.of(a1));

        List<AuditTrail> result = service.getByAction("REST_01", "ORDER_CANCELLED");

        assertEquals(1, result.size());
        assertEquals("ORDER_CANCELLED", result.get(0).getAction());
    }

    @Test
    void getRecentDelegatesToRepositoryWithLimit() {
        when(auditTrailRepository.findRecent("REST_01", 10)).thenReturn(List.of(
                AuditTrail.builder().id(1L).build(),
                AuditTrail.builder().id(2L).build()
        ));

        List<AuditTrail> result = service.getRecent("REST_01", 10);

        assertEquals(2, result.size());
        verify(auditTrailRepository).findRecent("REST_01", 10);
    }
}
