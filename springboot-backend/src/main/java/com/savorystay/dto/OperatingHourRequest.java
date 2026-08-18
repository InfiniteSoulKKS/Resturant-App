package com.savorystay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** One day's operating hours (dayOfWeek: 1 = Monday ... 7 = Sunday). */
public record OperatingHourRequest(
        @NotNull(message = "dayOfWeek is required (1=Monday .. 7=Sunday)")
        @Min(value = 1, message = "dayOfWeek must be between 1 and 7")
        @Max(value = 7, message = "dayOfWeek must be between 1 and 7")
        Integer dayOfWeek,

        LocalTime openTime,

        LocalTime closeTime,

        /** True = weekly holiday (closed all day). */
        Boolean closed) {
}
