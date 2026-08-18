package com.savorystay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Per-restaurant pre-order rules (cutoff time + advance horizon). */
public record UpdatePreOrderSettingsRequest(
        @NotNull(message = "cutoffTime is required (e.g. 09:00 — orders for date D close at cutoff on D-1)")
        LocalTime cutoffTime,

        @NotNull(message = "advanceDays is required")
        @Min(value = 1, message = "advanceDays must be at least 1")
        @Max(value = 30, message = "advanceDays cannot exceed 30")
        Integer advanceDays) {
}
