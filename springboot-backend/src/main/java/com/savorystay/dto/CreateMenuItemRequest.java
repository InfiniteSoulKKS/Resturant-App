package com.savorystay.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Menu item creation payload (manager/admin). */
public record CreateMenuItemRequest(
        @NotBlank(message = "Dish title is required")
        @Size(max = 255, message = "Dish title must be at most 255 characters")
        String title,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0", message = "Price cannot be negative")
        BigDecimal price,

        @NotBlank(message = "Category is required")
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
