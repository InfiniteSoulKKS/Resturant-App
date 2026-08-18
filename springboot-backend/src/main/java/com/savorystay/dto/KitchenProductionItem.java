package com.savorystay.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kitchen production view — shows batch quantities per dish for the current day.
 * Derived from actual active orders (not a separate source of truth).
 */
public record KitchenProductionItem(
        String menuItemId,
        String dishName,
        int requiredPlates,
        int preparedPlates,
        int remainingPlates,
        String urgency, // OVERDUE, DUE_SOON, NORMAL
        List<String> orderNumbers
) {}
