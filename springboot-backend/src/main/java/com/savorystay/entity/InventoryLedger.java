package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only stock-movement audit trail.
 * Every consumption or restock writes a row here.
 * No UPDATE or DELETE operations are exposed on this table.
 * Corresponds to the reference: inventory_ledger.
 */
@Entity
@Table(name = "inventory_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class InventoryLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false, length = 64)
    private String inventoryId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal delta; // negative for consumption, positive for restock

    @Column(nullable = false, length = 30)
    private String reason; // ORDER_CONSUMED, MANUAL_RESTOCK, WASTAGE, MANUAL_CORRECTION

    @Column(name = "reference_id", length = 64)
    private String referenceId; // order_id when reason=ORDER_CONSUMED

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}