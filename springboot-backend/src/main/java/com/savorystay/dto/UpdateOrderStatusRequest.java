package com.savorystay.dto;

import jakarta.validation.constraints.NotBlank;

/** Order status transition payload. */
public record UpdateOrderStatusRequest(
        @NotBlank(message = "orderId is required")
        String orderId,

        @NotBlank(message = "status is required")
        String status,

        String restaurantId,

        /** Reason for cancel/decline — preserved on the order for customer visibility. */
        String reason) {
}
