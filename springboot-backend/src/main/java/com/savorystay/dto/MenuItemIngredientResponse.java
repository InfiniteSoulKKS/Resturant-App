package com.savorystay.dto;

import com.savorystay.entity.MenuItemIngredient;

import java.math.BigDecimal;

/** View model for one recipe line of a menu item (used by the kitchen). */
public record MenuItemIngredientResponse(
        Long id,
        String menuItemId,
        String name,
        BigDecimal quantityPerUnit,
        String unit,
        String restaurantId) {

    public static MenuItemIngredientResponse from(MenuItemIngredient ing) {
        return new MenuItemIngredientResponse(
                ing.getId(),
                ing.getMenuItemId(),
                ing.getName(),
                ing.getQuantityPerUnit(),
                ing.getUnit(),
                ing.getRestaurantId());
    }
}
