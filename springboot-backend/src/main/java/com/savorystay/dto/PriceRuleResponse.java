package com.savorystay.dto;

import com.savorystay.entity.PriceRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** View model for a scheduled price rule on a menu item. */
public record PriceRuleResponse(
        Long id,
        String menuItemId,
        BigDecimal price,
        LocalDateTime effectiveFrom,
        LocalDateTime createdAt) {

    public static PriceRuleResponse from(PriceRule rule) {
        return new PriceRuleResponse(
                rule.getId(),
                rule.getMenuItemId(),
                rule.getPrice(),
                rule.getEffectiveFrom(),
                rule.getCreatedAt());
    }
}
