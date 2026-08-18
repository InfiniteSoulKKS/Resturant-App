package com.savorystay.service;

import com.savorystay.common.IngredientNormalization;
import com.savorystay.entity.Ingredient;
import com.savorystay.entity.InventoryLedger;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;
import com.savorystay.repository.IngredientRepository;
import com.savorystay.repository.InventoryLedgerRepository;
import com.savorystay.repository.MenuItemIngredientRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final MenuItemIngredientRepository menuItemIngredientRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;
    private final OutboxService outboxService;

    // ─── LIST ──────────────────────────────────────────────────────

    /** List all ingredients (active + inactive) for a restaurant. */
    public List<Ingredient> list(String restaurantId) {
        return ingredientRepository.findByRestaurantIdOrderByNameAsc(restaurantId);
    }

    /** List only active ingredients for a restaurant. */
    public List<Ingredient> listActive(String restaurantId) {
        return ingredientRepository.findByRestaurantIdAndActiveTrueOrderByNameAsc(restaurantId);
    }

    // ─── CREATE ────────────────────────────────────────────────────

    /**
     * Create a new ingredient with name normalization and duplicate prevention.
     * Rejects exact normalized duplicates within the same restaurant.
     */
    public Ingredient create(String restaurantId, Ingredient ingredient) {
        ingredient.setId(null);
        ingredient.setVersion(null);
        ingredient.setRestaurantId(restaurantId);

        // Normalize name
        String normalizedName = IngredientNormalization.normalize(ingredient.getName());
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Ingredient name cannot be blank");
        }
        ingredient.setNormalizedName(normalizedName);

        // Set display name if not provided
        if (ingredient.getDisplayName() == null || ingredient.getDisplayName().isBlank()) {
            ingredient.setDisplayName(ingredient.getName());
        }

        // Check for duplicate
        Optional<Ingredient> existing = ingredientRepository.findByRestaurantIdAndNormalizedName(
                restaurantId, normalizedName);
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                    "An ingredient named \"" + existing.get().getName() + "\" already exists in this restaurant");
        }

        // Set defaults
        if (ingredient.getActive() == null) ingredient.setActive(true);
        if (ingredient.getStockQuantity() == null) ingredient.setStockQuantity(BigDecimal.ZERO);
        if (ingredient.getReorderLevel() == null) ingredient.setReorderLevel(BigDecimal.ZERO);

        return ingredientRepository.save(ingredient);
    }

    /**
     * Find similar ingredient names for the "Did you mean?" suggestion.
     * Simple starts-with + contains heuristic — intentionally NOT fuzzy merging.
     */
    public List<Ingredient> findSimilar(String restaurantId, String name) {
        String normalizedName = IngredientNormalization.normalize(name);
        if (normalizedName.isBlank()) return List.of();
        return ingredientRepository.findByRestaurantIdOrderByNameAsc(restaurantId).stream()
                .filter(i -> {
                    String ingNormal = i.getNormalizedName();
                    return ingNormal.contains(normalizedName) || normalizedName.contains(ingNormal)
                        || ingNormal.startsWith(normalizedName.substring(0, Math.min(3, normalizedName.length())));
                })
                .limit(5)
                .toList();
    }

    // ─── UPDATE ────────────────────────────────────────────────────

    public Ingredient update(String restaurantId, String id, Ingredient updates) {
        Ingredient existing = ingredientRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));

        if (updates.getName() != null) {
            String normalizedName = IngredientNormalization.normalize(updates.getName());
            if (normalizedName.isBlank()) {
                throw new IllegalArgumentException("Ingredient name cannot be blank");
            }
            // Check duplicate if name changed
            if (!normalizedName.equals(existing.getNormalizedName())) {
                Optional<Ingredient> dup = ingredientRepository.findByRestaurantIdAndNormalizedName(
                        restaurantId, normalizedName);
                if (dup.isPresent()) {
                    throw new IllegalArgumentException(
                            "An ingredient named \"" + dup.get().getName() + "\" already exists in this restaurant");
                }
            }
            existing.setName(updates.getName());
            existing.setNormalizedName(normalizedName);
            if (updates.getDisplayName() != null) {
                existing.setDisplayName(updates.getDisplayName());
            } else {
                existing.setDisplayName(updates.getName());
            }
        }
        if (updates.getUnit() != null) existing.setUnit(updates.getUnit());
        if (updates.getCategory() != null) existing.setCategory(updates.getCategory());
        if (updates.getStockQuantity() != null) existing.setStockQuantity(updates.getStockQuantity());
        if (updates.getReorderLevel() != null) existing.setReorderLevel(updates.getReorderLevel());
        if (updates.getActive() != null) existing.setActive(updates.getActive());

        return ingredientRepository.save(existing);
    }

    // ─── SOFT DELETE (deactivate) ──────────────────────────────────

    /**
     * Soft-delete: set active = false. Hard delete is not allowed for
     * ingredients referenced by recipes or inventory history.
     */
    public Ingredient deactivate(String restaurantId, String id) {
        Ingredient ingredient = ingredientRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));
        ingredient.setActive(false);
        return ingredientRepository.save(ingredient);
    }

    /** Reactivate a previously deactivated ingredient. */
    public Ingredient reactivate(String restaurantId, String id) {
        Ingredient ingredient = ingredientRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));
        ingredient.setActive(true);
        return ingredientRepository.save(ingredient);
    }

    /** Toggle active status. */
    public Ingredient toggleActive(String restaurantId, String id) {
        Ingredient ingredient = ingredientRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));
        ingredient.setActive(!Boolean.TRUE.equals(ingredient.getActive()));
        return ingredientRepository.save(ingredient);
    }

    // ─── HARD DELETE ───────────────────────────────────────────────

    /**
     * Hard delete — only allowed if the ingredient has no recipe or inventory references.
     */
    public void delete(String restaurantId, String id) {
        Ingredient ingredient = ingredientRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));

        long usageCount = ingredientRepository.countRecipeUsage(id);
        if (usageCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete \"" + ingredient.getName() + "\" — it is used in " + usageCount
                    + " recipe(s). Deactivate it instead.");
        }
        ingredientRepository.deleteById(id);
    }

    // ─── SEARCH ────────────────────────────────────────────────────

    /** Search active ingredients by name fragment. */
    public List<Ingredient> search(String restaurantId, String query) {
        if (query == null || query.isBlank()) return listActive(restaurantId);
        return ingredientRepository.searchActive(restaurantId, query);
    }

    /** Search all ingredients (including inactive) for admin view. */
    public List<Ingredient> searchAll(String restaurantId, String query) {
        if (query == null || query.isBlank()) return list(restaurantId);
        return ingredientRepository.searchAll(restaurantId, query);
    }

    // ─── USAGE COUNT ───────────────────────────────────────────────

    /** Get the number of recipes that reference this ingredient. */
    public long getUsageCount(String ingredientId) {
        return ingredientRepository.countRecipeUsage(ingredientId);
    }

    // ─── INVENTORY RESERVATION ─────────────────────────────────────

    /**
     * Release ingredient reservation on order cancellation/decline.
     * Since we consume stock when PREPARING starts, releasing means adding back.
     * Only release if the order was in PREPARING state (stock was deducted).
     * NEW orders never consumed stock so there's nothing to release.
     */
    @Transactional
    public void releaseReservation(String orderId, String restaurantId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<String, BigDecimal> usageById = new LinkedHashMap<>();

        for (OrderItem item : items) {
            List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(item.getMenuItemId());
            for (MenuItemIngredient ing : ings) {
                BigDecimal total = ing.getQuantityPerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
                if (ing.getIngredientId() != null && !ing.getIngredientId().isBlank()) {
                    usageById.merge(ing.getIngredientId(), total, BigDecimal::add);
                }
            }
        }

        for (Map.Entry<String, BigDecimal> entry : usageById.entrySet()) {
            String ingredientId = entry.getKey();
            BigDecimal qtyToRelease = entry.getValue();
            ingredientRepository.findById(ingredientId).ifPresent(ing -> {
                ing.setStockQuantity(ing.getStockQuantity().add(qtyToRelease));
                ingredientRepository.save(ing);
                inventoryLedgerRepository.save(InventoryLedger.builder()
                        .inventoryId(ing.getId())
                        .delta(qtyToRelease)
                        .reason("CANCELLATION_RELEASE")
                        .referenceId(orderId)
                        .build());
            });
        }

        log.info("Released ingredient reservation for cancelled order {} ({} ingredients)", orderId, usageById.size());
    }

    // ─── EXISTING BUSINESS LOGIC (preserved) ──────────────────────

    /**
     * Deduct raw ingredient stock for every line item in an order.
     * Now uses ingredient IDs for aggregation instead of name-based matching.
     */
    @Transactional
    public void deductForOrder(String orderId, String restaurantId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<String, BigDecimal> usageById = new LinkedHashMap<>();
        Map<String, BigDecimal> usageByName = new HashMap<>();
        Map<String, String> unitsByName = new HashMap<>();

        for (OrderItem item : items) {
            List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(item.getMenuItemId());
            for (MenuItemIngredient ing : ings) {
                BigDecimal total = ing.getQuantityPerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
                // Aggregate by ingredient ID when available (preferred), fall back to name
                if (ing.getIngredientId() != null && !ing.getIngredientId().isBlank()) {
                    usageById.merge(ing.getIngredientId(), total, BigDecimal::add);
                } else {
                    usageByName.merge(ing.getName(), total, BigDecimal::add);
                    unitsByName.putIfAbsent(ing.getName(), ing.getUnit());
                }
            }
        }

        // Process ID-based deductions (new architecture)
        for (Map.Entry<String, BigDecimal> entry : usageById.entrySet()) {
            String ingredientId = entry.getKey();
            ingredientRepository.findById(ingredientId).ifPresent(ing -> {
                BigDecimal delta = entry.getValue().negate();
                ing.setStockQuantity(ing.getStockQuantity().add(delta).max(BigDecimal.ZERO));
                ingredientRepository.save(ing);
                auditAndAlert(ing, delta, orderId, restaurantId);
            });
        }

        // Process name-based deductions (legacy fallback)
        for (Map.Entry<String, BigDecimal> entry : usageByName.entrySet()) {
            String name = entry.getKey();
            ingredientRepository.findByRestaurantIdAndName(restaurantId, name).ifPresent(ing -> {
                BigDecimal delta = entry.getValue().negate();
                ing.setStockQuantity(ing.getStockQuantity().add(delta).max(BigDecimal.ZERO));
                ingredientRepository.save(ing);
                auditAndAlert(ing, delta, orderId, restaurantId);
            });
        }

        log.info("Deducted ingredients for order {} (audited + outbox events emitted)", orderId);
    }

    private void auditAndAlert(Ingredient ing, BigDecimal delta, String orderId, String restaurantId) {
        // Append-only audit: record the consumption
        inventoryLedgerRepository.save(InventoryLedger.builder()
                .inventoryId(ing.getId())
                .delta(delta)
                .reason("ORDER_CONSUMED")
                .referenceId(orderId)
                .build());

        // Transactional outbox: inventory.stock.decremented
        Map<String, Object> payload = new HashMap<>();
        payload.put("ingredientId", ing.getId());
        payload.put("restaurantId", restaurantId);
        payload.put("name", ing.getName());
        payload.put("stockQuantity", ing.getStockQuantity());
        payload.put("orderId", orderId);
        outboxService.record(ing.getId(), "inventory.stock.decremented", payload);

        // Low-stock alert fires automatically
        if (ing.getReorderLevel() != null
                && ing.getStockQuantity().compareTo(ing.getReorderLevel()) < 0) {
            Map<String, Object> low = new HashMap<>();
            low.put("ingredientId", ing.getId());
            low.put("restaurantId", restaurantId);
            low.put("name", ing.getName());
            low.put("stockQuantity", ing.getStockQuantity());
            low.put("reorderLevel", ing.getReorderLevel());
            outboxService.record(ing.getId(), "inventory.stock.low", low);
        }
    }

    /**
     * Compute required raw ingredients for the given date based on pre-orders.
     * Now aggregates by ingredient ID instead of name.
     */
    public Map<String, Object> forecastForDate(String restaurantId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now().plusDays(1);
        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.atTime(LocalTime.MAX);
        String dateStr = target.toString();

        List<Order> orders = orderRepository.findActiveOrdersBetween(restaurantId, dateStr, from, to);
        List<String> orderIds = orders.stream().map(Order::getId).toList();
        List<OrderItem> orderItems = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orderIds);

        Map<String, Integer> menuItemQuantities = new HashMap<>();
        for (OrderItem oi : orderItems) {
            menuItemQuantities.merge(oi.getMenuItemId(), oi.getQuantity(), Integer::sum);
        }

        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("ingredients", List.of());
        empty.put("dishes", List.of());
        if (menuItemQuantities.isEmpty()) {
            return empty;
        }

        List<MenuItemIngredient> recipes = menuItemIngredientRepository
                .findByMenuItemIdIn(new ArrayList<>(menuItemQuantities.keySet()));

        // Aggregate by ingredient ID (preferred) or by name (legacy)
        Map<String, BigDecimal> requiredById = new LinkedHashMap<>();
        Map<String, BigDecimal> requiredByName = new LinkedHashMap<>();
        Map<String, String> unitsById = new HashMap<>();
        Map<String, String> unitsByName = new HashMap<>();
        Map<String, Map<String, BigDecimal>> dishIngredientTotals = new LinkedHashMap<>();
        Map<String, Map<String, String>> dishIngredientUnits = new LinkedHashMap<>();

        for (MenuItemIngredient ing : recipes) {
            int qty = menuItemQuantities.getOrDefault(ing.getMenuItemId(), 0);
            BigDecimal amount = ing.getQuantityPerUnit().multiply(BigDecimal.valueOf(qty));

            String key = (ing.getIngredientId() != null && !ing.getIngredientId().isBlank())
                    ? ing.getIngredientId() : ing.getName();
            boolean useId = ing.getIngredientId() != null && !ing.getIngredientId().isBlank();

            if (useId) {
                requiredById.merge(ing.getIngredientId(), amount, BigDecimal::add);
                unitsById.putIfAbsent(ing.getIngredientId(), ing.getUnit());
            } else {
                requiredByName.merge(ing.getName(), amount, BigDecimal::add);
                unitsByName.putIfAbsent(ing.getName(), ing.getUnit());
            }

            dishIngredientTotals.computeIfAbsent(ing.getMenuItemId(), k -> new LinkedHashMap<>())
                    .merge(key, amount, BigDecimal::add);
            dishIngredientUnits.computeIfAbsent(ing.getMenuItemId(), k -> new LinkedHashMap<>())
                    .putIfAbsent(key, ing.getUnit());
        }

        // Build stock lookup
        Map<String, Ingredient> stockById = ingredientRepository.findByRestaurantIdOrderByNameAsc(restaurantId)
                .stream().collect(Collectors.toMap(Ingredient::getId, i -> i, (a, b) -> a));
        Map<String, Ingredient> stockByName = ingredientRepository.findByRestaurantIdOrderByNameAsc(restaurantId)
                .stream().collect(Collectors.toMap(Ingredient::getName, i -> i, (a, b) -> a));

        // Per-dish breakdown rows
        List<Map<String, Object>> dishRows = new ArrayList<>();
        Map<String, String> dishTitles = new LinkedHashMap<>();
        for (OrderItem oi : orderItems) {
            dishTitles.putIfAbsent(oi.getMenuItemId(), oi.getTitle());
        }
        for (Map.Entry<String, Integer> dishQty : menuItemQuantities.entrySet()) {
            String dishId = dishQty.getKey();
            Map<String, Object> dishRow = new LinkedHashMap<>();
            dishRow.put("menuItemId", dishId);
            dishRow.put("dish", dishTitles.getOrDefault(dishId, dishId));
            dishRow.put("plates", dishQty.getValue());
            List<Map<String, Object>> dishIngs = new ArrayList<>();
            Map<String, BigDecimal> totals = dishIngredientTotals.getOrDefault(dishId, Map.of());
            for (Map.Entry<String, BigDecimal> ing : totals.entrySet()) {
                Map<String, Object> ingRow = new LinkedHashMap<>();
                Ingredient master = stockById.get(ing.getKey());
                if (master == null) master = stockByName.get(ing.getKey());
                ingRow.put("name", master != null ? master.getName() : ing.getKey());
                ingRow.put("unit", dishIngredientUnits.getOrDefault(dishId, Map.of()).getOrDefault(ing.getKey(), "g"));
                ingRow.put("requiredQuantity", ing.getValue().setScale(2, RoundingMode.HALF_UP));
                dishIngs.add(ingRow);
            }
            dishRow.put("ingredients", dishIngs);
            dishRows.add(dishRow);
        }

        // Aggregated ingredient rows
        List<Map<String, Object>> result = new ArrayList<>();
        // Merge both maps into a single iteration
        Map<String, BigDecimal> allRequired = new LinkedHashMap<>();
        allRequired.putAll(requiredById);
        allRequired.putAll(requiredByName);

        for (Map.Entry<String, BigDecimal> entry : allRequired.entrySet()) {
            String key = entry.getKey();
            BigDecimal req = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            Ingredient ing = stockById.get(key);
            if (ing == null) ing = stockByName.get(key);
            String name = ing != null ? ing.getName() : key;
            String unit = ing != null ? ing.getUnit() : unitsById.getOrDefault(key, unitsByName.getOrDefault(key, "g"));
            BigDecimal current = ing != null ? ing.getStockQuantity() : BigDecimal.ZERO;
            BigDecimal shortfall = req.subtract(current).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("unit", unit);
            row.put("requiredQuantity", req);
            row.put("currentStock", current);
            row.put("shortfall", shortfall);
            row.put("needPurchase", shortfall.compareTo(BigDecimal.ZERO) > 0);
            row.put("reorderLevel", ing != null ? ing.getReorderLevel() : BigDecimal.ZERO);
            result.add(row);
        }

        result.sort(Comparator.comparing((Map<String, Object> m) -> (Boolean) m.get("needPurchase")).reversed()
                .thenComparing(m -> String.valueOf(m.get("name"))));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ingredients", result);
        response.put("dishes", dishRows);
        return response;
    }
}
