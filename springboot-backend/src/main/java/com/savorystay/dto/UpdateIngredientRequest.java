package com.savorystay.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** Partial ingredient update payload — all fields optional. */
public record UpdateIngredientRequest(
        String name,
        String displayName,
        String unit,
        String category,

        @DecimalMin(value = "0", message = "Stock quantity cannot be negative")
        BigDecimal stockQuantity,

        @DecimalMin(value = "0", message = "Reorder level cannot be negative")
        BigDecimal reorderLevel,

        /** Set to true/false to activate/deactivate the ingredient. */
        Boolean active) {
}
