package com.savorystay.service;

import com.savorystay.dto.MenuItemIngredientRequest;
import com.savorystay.entity.Ingredient;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.entity.PriceRule;
import com.savorystay.repository.IngredientRepository;
import com.savorystay.repository.MenuItemIngredientRepository;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.PriceRuleRepository;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.tenant.TenantContext;
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
    private final RealtimeService realtimeService;
    private final OrderItemRepository orderItemRepository;
    private final AuditService auditService;

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
        // P0.30: Audit trail for menu creation
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            auditService.record(saved.getRestaurantId(), userId, role,
                    "MENU_ITEM_CREATED", "MENU_ITEM", saved.getId(),
                    Map.of("title", saved.getTitle(), "price", saved.getPrice(),
                           "category", saved.getCategory(), "status", saved.getStatus()),
                    "Menu item created");
        } catch (Exception ignored) {}
        return saved;
    }

    @Transactional
    public MenuItem update(String id, String restaurantId, MenuItem updates,
                           List<MenuItemIngredientRequest> ingredients) {
        MenuItem existing = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));

        // Capture old status BEFORE mutation so we can detect changes
        String oldStatus = existing.getStatus();
        // P0.30: Capture old values for audit
        BigDecimal oldPrice = existing.getPrice();
        String oldTitle = existing.getTitle();

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

        // Broadcast if status changed — compare new status against OLD status
        if (updates.getStatus() != null && !updates.getStatus().equals(oldStatus)) {
            broadcastAvailability(restaurantId, id, saved.getTitle(), saved.getStatus(), saved.getDailyPlateCount());
        }

        if (ingredients != null) {
            ingredientRepository.deleteByMenuItemId(id);
            saveIngredients(saved.getId(), saved.getRestaurantId(), ingredients);
        }

        // P0.30: Audit trail for menu updates
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            Map<String, Object> changes = new HashMap<>();
            if (updates.getTitle() != null) changes.put("title", oldTitle + " → " + saved.getTitle());
            if (updates.getPrice() != null && oldPrice != null && oldPrice.compareTo(saved.getPrice()) != 0)
                changes.put("price", oldPrice + " → " + saved.getPrice());
            if (updates.getStatus() != null) changes.put("status", oldStatus + " → " + saved.getStatus());
            if (!changes.isEmpty()) {
                auditService.record(restaurantId, userId, role,
                        "MENU_ITEM_UPDATED", "MENU_ITEM", saved.getId(),
                        changes, "Menu item updated");
            }
        } catch (Exception ignored) {}
        return saved;
    }

    @Transactional
    public void delete(String id, String restaurantId) {
        MenuItem existing = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        ingredientRepository.deleteByMenuItemId(id);
        menuItemRepository.deleteById(id);
        // P0.30: Audit trail for menu deletion
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            auditService.record(restaurantId, userId, role,
                    "MENU_ITEM_DELETED", "MENU_ITEM", id,
                    Map.of("title", existing.getTitle(), "price", existing.getPrice()),
                    "Menu item deleted");
        } catch (Exception ignored) {}
    }

    @Transactional
    public MenuItem updateStatus(String id, String restaurantId, String newStatus) {
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        String oldStatus = item.getStatus();
        item.setStatus(newStatus);
        MenuItem saved = menuItemRepository.save(item);

        // Broadcast availability change to all connected customers in real time via SSE
        broadcastAvailability(restaurantId, id, saved.getTitle(), newStatus, saved.getDailyPlateCount());

        // P0.30: Audit trail for sold-out changes
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            String action = "Sold Out".equals(newStatus) ? "DISH_MARKED_SOLD_OUT" : "DISH_RESTORED_AVAILABLE";
            auditService.record(restaurantId, userId, role,
                    action, "MENU_ITEM", id,
                    Map.of("title", saved.getTitle(), "oldStatus", oldStatus, "newStatus", newStatus),
                    action);
        } catch (Exception ignored) {}
        return saved;
    }

    /**
     * Broadcast a menu availability change to all connected users via SSE.
     * The frontend listener updates the cart and shows a toast in real time.
     */
    private void broadcastAvailability(String restaurantId, String menuItemId,
                                         String title, String status, Integer dailyPlateCount) {
        // Compute remaining plates for today if there's a daily cap
        Integer remainingPlates = null;
        if (dailyPlateCount != null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            long ordered = orderItemRepository.countPlatesOrderedForItem(
                    menuItemId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            remainingPlates = Math.max(0, dailyPlateCount - (int) ordered);
        }

        Map<String, Object> event = new HashMap<>();
        event.put("menuItemId", menuItemId);
        event.put("title", title);
        event.put("status", status);
        event.put("dailyPlateCount", dailyPlateCount);
        event.put("remainingPlates", remainingPlates);
        event.put("restaurantId", restaurantId);
        event.put("timestamp", LocalDateTime.now().toString());

        // Push to all connected users (customers browsing the menu)
        realtimeService.broadcastToAllUsers("menu_availability", event);
        // Also push to restaurant staff channel
        realtimeService.pushToRestaurant(restaurantId, "menu_availability", event);

        log.info("[SSE] Broadcast menu availability: {} → {} (restaurant={})", title, status, restaurantId);
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
