package com.savorystay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Staff account creation payload (manager / chef).
 * <p>
 * A staff member must provide at least one of {@code email} or {@code phone}
 * (validated in the service layer, not here — the two are interchangeable).
 * Some staff have no email address; they are created with a phone number only.
 */
public record CreateStaffRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 50, message = "Username must be at most 50 characters")
        String username,

        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must be at most 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 100, message = "Password must be at most 100 characters")
        String password,

        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone,

        @Size(max = 30, message = "Role must be at most 30 characters")
        String role,

        String restaurantId) {
}
