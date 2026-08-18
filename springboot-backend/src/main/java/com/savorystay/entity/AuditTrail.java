package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Reusable audit trail for important business mutations.
 * Append-only — no UPDATE or DELETE operations exposed.
 *
 * Covers: menu changes, price changes, recipe changes, ingredient CRUD,
 * inventory adjustments, pre-order config, staff role changes, order
 * cancellation/decline, refunds, payment actions.
 */
@Entity
@Table(name = "audit_trail", indexes = {
        @Index(name = "idx_audit_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_actor", columnList = "actor_user_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 50)
    private String action; // e.g. "ORDER_CANCELLED", "PAYMENT_REFUNDED", "INGREDIENT_CREATED", "PRICE_CHANGED"

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // ORDER, PAYMENT, INGREDIENT, MENU_ITEM, INVENTORY, PREORDER_CONFIG

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue; // JSON snapshot of previous state (optional)

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue; // JSON snapshot of new state (optional)

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}
