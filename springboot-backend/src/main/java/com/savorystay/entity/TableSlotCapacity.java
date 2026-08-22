package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table slot capacity record for atomic table reservations.
 * Prevents race conditions where two concurrent DINE_IN checkouts
 * both pass the availability check and oversell the last table.
 *
 * Each row represents: restaurant X, date Y, time_slot Z, table_type T
 * has capacity C, with R currently reserved.
 *
 * At checkout: atomically reserve using optimistic locking (version).
 */
@Entity
@Table(name = "table_slot_capacity",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_table_slot_capacity",
           columnNames = {"restaurant_id", "business_date", "time_slot", "table_type"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableSlotCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /** Time slot label, e.g. "12:00 PM", "7:00 PM". */
    @Column(name = "time_slot", nullable = false, length = 100)
    private String timeSlot;

    /** Table type: "2-Seater", "4-Seater", "6-Seater". */
    @Column(name = "table_type", nullable = false, length = 30)
    private String tableType;

    /** How many tables of this type exist. */
    @Column(nullable = false)
    private Integer totalCapacity;

    /** How many tables are currently reserved. */
    @Column(nullable = false)
    @Builder.Default
    private Integer reservedCount = 0;

    /** Optimistic lock version. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (reservedCount == null) reservedCount = 0;
        if (version == null) version = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int remaining() {
        return Math.max(0, totalCapacity - reservedCount);
    }
}
