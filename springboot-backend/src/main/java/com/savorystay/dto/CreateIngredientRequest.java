package com.savorystay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Ingredient creation payload. */
public record CreateIngredientRequest(
        @NotBlank(message = "Ingredient name is required")
        String name,

        /** Human-readable display name. Falls back to name if null. */
        String displayName,

        @NotBlank(message = "Unit is required")
        String unit,

        /** Optional category (Meat, Grains, Dairy, Spices, etc.). */
        String category,

        @NotNull(message = "Stock quantity is required")
        @DecimalMin(value = "0", message = "Stock quantity cannot be negative")
        BigDecimal stockQuantity,

        @DecimalMin(value = "0", message = "Reorder level cannot be negative")
        BigDecimal reorderLevel,

        /** Kitchen warning threshold — when stock drops to/below this, kitchen sees an amber warning. */
        @DecimalMin(value = "0", message = "Low stock threshold cannot be negative")
        BigDecimal lowStockThreshold) {
}
