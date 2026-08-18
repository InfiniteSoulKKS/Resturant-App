package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ingredient master data tracked per restaurant.
 *
 * The ingredient's {@code id} is the canonical identity — recipes and inventory
 * reference this ID, never free-text names. The {@code normalizedName} field
 * ensures uniqueness within a restaurant after case-folding and whitespace
 * collapsing.
 */
@Entity
@Table(name = "ingredients",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_ingredient_restaurant_normalized",
           columnNames = {"restaurant_id", "normalized_name"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {

    @Id
    private String id;

    /**
     * Optimistic-lock version. Stock is a hot write path (order preparation
     * deducts stock across ingredients), so concurrent read-modify-write cycles
     * are guarded here: a stale update throws ObjectOptimisticLockingFailureException
     * instead of silently losing stock.
     */
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    /** Human-readable display name (e.g. "Chicken Breast"). */
    @Column(nullable = false, length = 100)
    private String name;

    /** Display name — the name the user sees. Falls back to {@code name} if null. */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** Lowercase, trimmed, whitespace-collapsed form used for uniqueness enforcement. */
    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    /** Canonical/base unit for this ingredient (kg, g, L, ml, piece). */
    @Column(nullable = false, length = 20)
    private String unit;

    /** Optional category for grouping (Meat, Grains, Dairy, Spices, etc.). */
    @Column(length = 50)
    private String category;

    @Column(name = "stock_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockQuantity;

    @Column(name = "reorder_level", precision = 12, scale = 3)
    private BigDecimal reorderLevel;

    /** Active = true means this ingredient is available for new recipes. Inactive = soft-deleted. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = IdGenerator.newId("ING");
        if (stockQuantity == null) stockQuantity = BigDecimal.ZERO;
        if (reorderLevel == null) reorderLevel = BigDecimal.ZERO;
        if (active == null) active = true;
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
