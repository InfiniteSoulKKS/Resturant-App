package com.savorystay.dto;

import jakarta.validation.constraints.NotBlank;

/** Password login payload. */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password) {
}
