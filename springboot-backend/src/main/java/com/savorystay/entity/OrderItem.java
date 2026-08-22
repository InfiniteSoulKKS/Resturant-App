package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item inside an order.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    private String id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "menu_item_id", length = 64)
    private String menuItemId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * P0.13: JSON snapshot of ingredient requirements at order time.
     * Preserves historical recipe data so future recipe changes don't
     * affect past order forecasts.
     * Format: [{"ingredientId":"...","name":"...","quantity":250,"unit":"g"},...]
     */
    @Column(name = "ingredient_snapshot", columnDefinition = "TEXT")
    private String ingredientSnapshot;

    @Column(length = 255)
    private String notes;

    @PrePersist
    protected void onCreate() {
        // UUID-based so multiple items created in the same millisecond never collide
        if (id == null) id = "OI_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
