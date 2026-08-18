package com.savorystay.dto;

import java.util.List;

/**
 * Request body for POST /api/v1/menu/availability-check.
 * Checks whether all items in a customer's cart are still available
 * before they proceed to payment.
 */
public record CartAvailabilityRequest(
        String restaurantId,
        List<CartItemCheck> items
) {
    public record CartItemCheck(
            String menuItemId,
            int quantity
    ) {}
}
