package com.savorystay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Partial update payload for a restaurant — all fields optional. */
public record UpdateRestaurantRequest(
        @Size(max = 100, message = "Restaurant name must be at most 100 characters")
        String name,

        String slug,
        String description,
        String address,
        String city,
        String cuisine,
        String phone,

        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must be at most 100 characters")
        String email,

        String logoUrl,
        String status,
        String currency) {
}
