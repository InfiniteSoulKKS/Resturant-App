package com.savorystay.dto;

import jakarta.validation.constraints.NotBlank;

/** OTP login payload (username + verified 6-digit code + delivery channel). */
public record LoginWithOtpRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "OTP code is required")
        String otpCode,

        @NotBlank(message = "Channel (EMAIL, SMS, WHATSAPP) is required")
        String channel) {
}
