package com.savorystay.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Partial menu-item update payload — all fields optional. */
public record UpdateMenuItemRequest(
        @Size(max = 255, message = "Dish title must be at most 255 characters")
        String title,

        String description,

        @DecimalMin(value = "0", message = "Price cannot be negative")
        BigDecimal price,

        @Size(max = 50, message = "Category must be at most 50 characters")
        String category,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl,

        String status,
        Boolean isVeg,
        String spiceLevel,
        Integer prepMinutes,

        String restaurantId,

        @Valid
        List<MenuItemIngredientRequest> ingredients) {
}
