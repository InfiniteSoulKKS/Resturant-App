package com.savorystay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** One line item inside a customer order placement. */
public record OrderItemRequest(
        @NotBlank(message = "menuItemId is required")
        String menuItemId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        String notes) {
}
