package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily plate capacity record for a menu item.
 * Enforces atomic plate limits at checkout to prevent race conditions
 * where two concurrent orders both pass the availability check.
 *
 * At checkout: atomically reserve the requested quantity using
 * optimistic locking (version field). If version mismatch → retry or fail
 * with PLATE_CAPACITY_EXCEEDED.
 */
@Entity
@Table(name = "plate_capacity",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_plate_capacity",
           columnNames = {"menu_item_id", "business_date"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlateCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_item_id", nullable = false, length = 64)
    private String menuItemId;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /** Maximum plates allowed per day for this dish. */
    @Column(nullable = false)
    private Integer capacity;

    /** How many plates have been reserved by orders. */
    @Column(nullable = false)
    @Builder.Default
    private Integer reservedCount = 0;

    /** Optimistic lock version — concurrent updates are detected via version mismatch. */
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
        return Math.max(0, capacity - reservedCount);
    }
}
