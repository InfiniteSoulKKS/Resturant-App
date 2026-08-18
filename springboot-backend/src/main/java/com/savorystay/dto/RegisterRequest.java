package com.savorystay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. The OTP fields are validated by the auth flow itself
 * (they gate the whole registration); the bean-constraints below cover the
 * account fields that must always be well-formed.
 */
public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 50, message = "Username must be at most 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must be at most 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 100, message = "Password must be at most 100 characters")
        String password,

        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone,

        String otpCode,

        String otpChannel) {
}
