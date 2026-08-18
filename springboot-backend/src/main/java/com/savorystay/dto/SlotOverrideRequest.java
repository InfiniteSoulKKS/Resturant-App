package com.savorystay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** Manager opens/closes a specific date for a dish. */
public record SlotOverrideRequest(
        @NotNull(message = "date is required")
        LocalDate date,

        @NotBlank(message = "action is required (OPEN or CLOSE)")
        @Pattern(regexp = "OPEN|CLOSE", message = "action must be OPEN or CLOSE")
        String action) {
}
