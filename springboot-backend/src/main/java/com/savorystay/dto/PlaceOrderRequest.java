package com.savorystay.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Customer order placement payload. */
public record PlaceOrderRequest(
        @NotBlank(message = "restaurantId is required")
        String restaurantId,

        String customerName,

        String customerPhone,

        @Email(message = "Customer email must be a valid email address")
        String customerEmail,

        String orderType,

        @Min(value = 1, message = "Table number must be positive")
        Integer tableNumber,

        @Min(value = 1, message = "Guests must be positive")
        Integer guests,

        String timeSlot,
        String pickupTime,
        String paymentMethod,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemRequest> items) {
}
