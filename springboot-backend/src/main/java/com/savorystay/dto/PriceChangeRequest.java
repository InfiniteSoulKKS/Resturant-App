package com.savorystay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Price-change payload: new price + optional future effective timestamp. */
public record PriceChangeRequest(
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0", message = "Price cannot be negative")
        BigDecimal price,

        LocalDateTime effectiveFrom) {
}
