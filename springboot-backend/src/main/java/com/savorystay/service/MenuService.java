package com.savorystay.service;

import com.savorystay.dto.MenuItemIngredientRequest;
import com.savorystay.entity.Ingredient;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.entity.PriceRule;
import com.savorystay.repository.IngredientRepository;
import com.savorystay.repository.MenuItemIngredientRepository;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.PriceRuleRepository;
import com.savorystay.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final MenuItemIngredientRepository ingredientRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final RestaurantRepository restaurantRepository;
    private final IngredientRepository ingredientMasterRepository;

    /** True when the restaurant exists and is not suspended/offline. */
    public boolean isRestaurantActive(String restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .map(r -> "ACTIVE".equals(r.getStatus()))
                .orElse(false);
    }

    public List<MenuItem> listMenu(String restaurantId, boolean onlyAvailable) {
        List<MenuItem> menu = onlyAvailable
                ? menuItemRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, "Available")
                : menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        applyEffectivePrices(menu);
        return menu;
    }

    public Optional<MenuItem> get(String id, String restaurantId) {
        Optional<MenuItem> item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId);
        item.ifPresent(m -> applyEffectivePrices(List.of(m)));
        return item;
    }

    public BigDecimal getEffectivePrice(String menuItemId, BigDecimal basePrice) {
        Optional<PriceRule> rule = priceRuleRepository.findCurrentEffective(menuItemId, LocalDateTime.now());
        return rule.map(PriceRule::getPrice).orElse(basePrice);
    }

    private void applyEffectivePrices(List<MenuItem> items) {
        if (items.isEmpty()) return;
        List<String> ids = items.stream().map(MenuItem::getId).toList();
        List<PriceRule> rules = priceRuleRepository.findEffectiveIn(ids, LocalDateTime.now());
        if (rules.isEmpty()) return;

        Map<String, BigDecimal> effective = new HashMap<>();
        for (PriceRule rule : rules) {
            effective.putIfAbsent(rule.getMenuItemId(), rule.getPrice());
        }
        for (MenuItem item : items) {
            BigDecimal price = effective.get(item.getId());
            if (price != null) item.setPrice(price);
        }
    }

    @Transactional
    public PriceRule schedulePriceChange(String menuItemId, String restaurantId,
                                         BigDecimal price, LocalDateTime effectiveFrom) {
        menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        PriceRule rule = PriceRule.builder()
                .menuItemId(menuItemId)
                .price(price)
                .effectiveFrom(effectiveFrom != null ? effectiveFrom : LocalDateTime.now())
                .build();
        PriceRule saved = priceRuleRepository.save(rule);

        if (!saved.getEffectiveFrom().isAfter(LocalDateTime.now())) {
            menuItemRepository.findById(menuItemId).ifPresent(item -> {
                item.setPrice(price);
                menuItemRepository.save(item);
            });
        }
        return saved;
    }

    @Transactional
    public MenuItem create(MenuItem item, List<MenuItemIngredientRequest> ingredients) {
        item.setId(null);
        MenuItem saved = menuItemRepository.save(item);
        if (ingredients != null) {
            saveIngredients(saved.getId(), saved.getRestaurantId(), ingredients);
        }
        return saved;
    }

    @Transactional
    public MenuItem update(String id, String restaurantId, MenuItem updates,
                           List<MenuItemIngredientRequest> ingredients) {
        MenuItem existing = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));

        if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getPrice() != null) existing.setPrice(updates.getPrice());
        if (updates.getCategory() != null) existing.setCategory(updates.getCategory());
        if (updates.getImageUrl() != null) existing.setImageUrl(updates.getImageUrl());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getIsVeg() != null) existing.setIsVeg(updates.getIsVeg());
        if (updates.getSpiceLevel() != null) existing.setSpiceLevel(updates.getSpiceLevel());
        if (updates.getPrepMinutes() != null) existing.setPrepMinutes(updates.getPrepMinutes());

        MenuItem saved = menuItemRepository.save(existing);

        if (ingredients != null) {
            ingredientRepository.deleteByMenuItemId(id);
            saveIngredients(saved.getId(), saved.getRestaurantId(), ingredients);
        }
        return saved;
    }

    @Transactional
    public void delete(String id, String restaurantId) {
        menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        ingredientRepository.deleteByMenuItemId(id);
        menuItemRepository.deleteById(id);
    }

    @Transactional
    public MenuItem updateStatus(String id, String restaurantId, String newStatus) {
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        item.setStatus(newStatus);
        return menuItemRepository.save(item);
    }

    public List<MenuItemIngredient> ingredientsFor(String menuItemId, String restaurantId) {
        menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        return ingredientRepository.findByMenuItemId(menuItemId);
    }

    /**
     * Save recipe ingredients with validation:
     * - Ingredient IDs must belong to the current restaurant
     * - Ingredient must be active
     * - Same ingredient cannot appear twice in one recipe
     */
    private void saveIngredients(String menuItemId, String restaurantId, List<MenuItemIngredientRequest> ingredients) {
        List<MenuItemIngredient> entities = new ArrayList<>();
        Set<String> seenIngredientIds = new HashSet<>();

        for (MenuItemIngredientRequest ing : ingredients) {
            String ingredientId = ing.ingredientId();
            String requestedName = ing.name();
            String finalName = requestedName;

            // Validate ingredient ID if provided
            if (ingredientId != null && !ingredientId.isBlank()) {
                // Prevent duplicate ingredient in same recipe
                if (!seenIngredientIds.add(ingredientId)) {
                    throw new IllegalArgumentException(
                            "Duplicate ingredient in recipe: \"" + requestedName + "\" — edit the existing entry instead.");
                }

                // Validate ingredient belongs to this restaurant
                Ingredient master = ingredientMasterRepository.findByIdAndRestaurantId(ingredientId, restaurantId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Ingredient \"" + requestedName + "\" does not belong to this restaurant"));

                // Validate ingredient is active
                if (!Boolean.TRUE.equals(master.getActive())) {
                    throw new IllegalArgumentException(
                            "Ingredient \"" + master.getName() + "\" is inactive and cannot be used in new recipes");
                }

                // Use the master's name as authoritative
                finalName = master.getName();
            }

            final String resolvedName = finalName;
            entities.add(MenuItemIngredient.builder()
                    .menuItemId(menuItemId)
                    .ingredientId(ingredientId)
                    .restaurantId(restaurantId)
                    .name(resolvedName)
                    .quantityPerUnit(ing.quantityPerUnit())
                    .unit(ing.unit())
                    .build());
        }
        ingredientRepository.saveAll(entities);
    }
}
