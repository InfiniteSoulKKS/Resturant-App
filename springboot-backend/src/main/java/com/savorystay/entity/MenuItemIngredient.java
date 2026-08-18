package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * Maps a menu item to one raw ingredient and the quantity required per serving.
 * Used to compute next-day ingredient requirements from pre-orders.
 *
 * The recipe stores a reference to the ingredient master via {@code ingredientId},
 * not a free-text ingredient name. The {@code name} field is kept for backward
 * compatibility and display purposes but is always derived from the ingredient master.
 */
@Entity
@Table(name = "menu_item_ingredients",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_menu_item_ingredient",
           columnNames = {"menu_item_id", "ingredient_id"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_item_id", nullable = false, length = 64)
    private String menuItemId;

    /** FK to the ingredient master. This is the canonical identity. */
    @Column(name = "ingredient_id", nullable = false, length = 64)
    private String ingredientId;

    /** Ingredient name (denormalized from master for display; kept for backward compat). */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "quantity_per_unit", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityPerUnit;

    @Column(nullable = false, length = 20)
    private String unit; // kg, g, L, ml, count

    @Column(name = "restaurant_id", length = 64)
    private String restaurantId;
}
