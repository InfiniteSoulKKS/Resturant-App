package com.savorystay.dto;

import com.savorystay.entity.Ingredient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * View model for an ingredient.
 * The optimistic-lock {@code version} is internal — clients neither need it
 * nor should be able to influence it.
 */
public record IngredientResponse(
        String id,
        String restaurantId,
        String name,
        String displayName,
        String unit,
        String category,
        BigDecimal stockQuantity,
        BigDecimal reorderLevel,
        BigDecimal lowStockThreshold,
        Boolean active,
        LocalDateTime updatedAt) {

    public static IngredientResponse from(Ingredient ing) {
        return new IngredientResponse(
                ing.getId(),
                ing.getRestaurantId(),
                ing.getName(),
                ing.getDisplayName() != null ? ing.getDisplayName() : ing.getName(),
                ing.getUnit(),
                ing.getCategory(),
                ing.getStockQuantity(),
                ing.getReorderLevel(),
                ing.getLowStockThreshold(),
                ing.getActive(),
                ing.getUpdatedAt());
    }
}
