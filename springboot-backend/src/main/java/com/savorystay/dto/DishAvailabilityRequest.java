package com.savorystay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Weekly pre-order availability for a dish: list of weekdays (1=Monday..7=Sunday). */
public record DishAvailabilityRequest(
        @NotEmpty(message = "At least one weekday must be selected")
        List<@Min(value = 1, message = "dayOfWeek must be between 1 and 7")
             @Max(value = 7, message = "dayOfWeek must be between 1 and 7")
             Integer> days) {
}
