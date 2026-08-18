package com.savorystay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Recipe line for a menu item.
 * The {@code ingredientId} is the canonical reference to the ingredient master.
 * The {@code name} field is kept for backward compatibility.
 */
public record MenuItemIngredientRequest(
        /** ID of the ingredient master entry. Required for new recipes. */
        String ingredientId,

        /** Ingredient name (denormalized from master; kept for backward compat). */
        @NotBlank(message = "Ingredient name is required")
        String name,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be positive")
        BigDecimal quantityPerUnit,

        @NotBlank(message = "Unit is required")
        String unit) {
}
