package com.savorystay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Super-admin restaurant registration payload.
 * Admin credentials are optional in the service (a restaurant can be created
 * without an admin account), so they are not bean-constrained — only the
 * restaurant name is mandatory.
 */
public record CreateRestaurantRequest(
        @NotBlank(message = "Restaurant name is required")
        @Size(max = 100, message = "Restaurant name must be at most 100 characters")
        String name,

        String description,
        String address,
        String city,
        String cuisine,
        String phone,

        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must be at most 100 characters")
        String email,

        String logoUrl,
        String currency,
        String adminUsername,

        @Email(message = "Admin email must be a valid email address")
        String adminEmail,

        String adminPassword) {
}
