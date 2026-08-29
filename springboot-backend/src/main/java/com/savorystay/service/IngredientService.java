package com.savorystay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.savorystay.tenant.TenantContext;
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
    private final AuditService auditService;
    private final ChannelDeliveryService channelDeliveryService;
    private final EmailTemplateService emailTemplateService;
    private final com.savorystay.repository.RestaurantRepository restaurantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        Ingredient saved = ingredientRepository.save(ingredient);
        // P0.30: Audit trail for ingredient creation
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            auditService.record(restaurantId, userId, role,
                    "INGREDIENT_CREATED", "INGREDIENT", saved.getId(),
                    Map.of("name", saved.getName(), "unit", saved.getUnit(),
                           "stockQuantity", saved.getStockQuantity()),
                    "Ingredient created");
        } catch (Exception ignored) {}
        return saved;
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
        if (updates.getLowStockThreshold() != null) existing.setLowStockThreshold(updates.getLowStockThreshold());
        if (updates.getActive() != null) existing.setActive(updates.getActive());

        Ingredient saved = ingredientRepository.save(existing);
        // P0.30: Audit trail for ingredient update
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            auditService.record(restaurantId, userId, role,
                    "INGREDIENT_UPDATED", "INGREDIENT", saved.getId(),
                    Map.of("name", saved.getName(), "stockQuantity", saved.getStockQuantity(),
                           "active", saved.getActive()),
                    "Ingredient updated");
        } catch (Exception ignored) {}
        return saved;
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

    // ─── RESTOCK REQUEST ────────────────────────────────────────────

    /**
     * Send a restock request email to the restaurant's contact address.
     * Called by kitchen staff from the dashboard when an ingredient is low or depleted.
     *
     * @return success message
     */
    public String requestRestock(String restaurantId, String ingredientId, String requestedBy) {
        Ingredient ingredient = ingredientRepository.findByIdAndRestaurantId(ingredientId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));

        var restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        if (restaurant.getEmail() == null || restaurant.getEmail().isBlank()) {
            throw new IllegalArgumentException("Restaurant has no email address configured — cannot send restock request");
        }

        String subject = "Restock Request: " + (ingredient.getDisplayName() != null ? ingredient.getDisplayName() : ingredient.getName());
        String html = emailTemplateService.restockRequestEmail(
                ingredient.getDisplayName() != null ? ingredient.getDisplayName() : ingredient.getName(),
                ingredient.getStockQuantity(),
                ingredient.getReorderLevel(),
                ingredient.getUnit(),
                requestedBy != null ? requestedBy : "Kitchen Staff",
                restaurant.getName());

        channelDeliveryService.sendHtmlEmail(restaurant.getEmail(), subject, html);

        // Audit trail
        try {
            String userId = TenantContext.getUserId();
            String role = TenantContext.getRole();
            auditService.record(restaurantId, userId, role,
                    "RESTOCK_REQUESTED", "INGREDIENT", ingredientId,
                    Map.of("name", ingredient.getName(),
                           "stockQuantity", ingredient.getStockQuantity(),
                           "requestedBy", requestedBy != null ? requestedBy : "Kitchen Staff",
                           "email", restaurant.getEmail()),
                    "Restock request email sent to " + restaurant.getEmail());
        } catch (Exception ignored) {}

        log.info("Restock request sent for ingredient {} ({}) to {} by {}",
                ingredient.getName(), ingredientId, restaurant.getEmail(), requestedBy);

        return "Restock request email sent to " + restaurant.getEmail();
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

    // ─── INGREDIENT SNAPSHOT PARSING ─────────────────────────────────

    /**
     * P0.13: Parse the JSON ingredient snapshot stored on OrderItem.
     * Format: [{"ingredientId":"...","name":"...","quantity":250,"unit":"g"},...]
     * Returns empty list if snapshot is null, blank, or malformed (graceful fallback).
     */
    private List<Map<String, Object>> parseIngredientSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return List.of();
        try {
            return objectMapper.readValue(snapshot, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Failed to parse ingredient snapshot, falling back to live recipe: {}", e.getMessage());
            return List.of();
        }
    }

    // ─── INVENTORY RESERVATION ─────────────────────────────────────

    /**
     * Release ingredient reservation on order cancellation/decline.
     * Since we consume stock when PREPARING starts, releasing means adding back.
     * Only release if the order was in PREPARING state (stock was deducted).
     * NEW orders never consumed stock so there's nothing to release.
     *
     * P0.13: Uses the ingredient snapshot from OrderItem to release exactly
     * what was originally deducted, so post-order recipe changes are harmless.
     * Falls back to live recipe for orders placed before snapshots existed.
     */
    @Transactional
    public void releaseReservation(String orderId, String restaurantId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<String, BigDecimal> usageById = new LinkedHashMap<>();
        Map<String, BigDecimal> usageByName = new LinkedHashMap<>();
        boolean usedSnapshot = false;

        for (OrderItem item : items) {
            List<Map<String, Object>> snapshot = parseIngredientSnapshot(item.getIngredientSnapshot());
            if (!snapshot.isEmpty()) {
                // Use the frozen recipe from order time
                usedSnapshot = true;
                for (Map<String, Object> entry : snapshot) {
                    String ingredientId = entry.get("ingredientId") != null ? entry.get("ingredientId").toString() : null;
                    String name = entry.get("name") != null ? entry.get("name").toString() : null;
                    BigDecimal qtyPerUnit = entry.get("quantity") != null
                            ? new BigDecimal(entry.get("quantity").toString())
                            : BigDecimal.ZERO;
                    BigDecimal total = qtyPerUnit.multiply(BigDecimal.valueOf(item.getQuantity()));

                    if (ingredientId != null && !ingredientId.isBlank()) {
                        usageById.merge(ingredientId, total, BigDecimal::add);
                    } else if (name != null && !name.isBlank()) {
                        usageByName.merge(name, total, BigDecimal::add);
                    }
                }
            } else {
                // Fallback: query live recipe (pre-snapshot orders)
                List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(item.getMenuItemId());
                for (MenuItemIngredient ing : ings) {
                    BigDecimal total = ing.getQuantityPerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
                    if (ing.getIngredientId() != null && !ing.getIngredientId().isBlank()) {
                        usageById.merge(ing.getIngredientId(), total, BigDecimal::add);
                    } else {
                        usageByName.merge(ing.getName(), total, BigDecimal::add);
                    }
                }
            }
        }

        // Release ID-based reservations
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

        // Release name-based reservations (snapshot or legacy fallback)
        for (Map.Entry<String, BigDecimal> entry : usageByName.entrySet()) {
            String name = entry.getKey();
            BigDecimal qtyToRelease = entry.getValue();
            ingredientRepository.findByRestaurantIdAndName(restaurantId, name).ifPresent(ing -> {
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

        log.info("Released ingredient reservation for order {} ({} ingredients, snapshot={})",
                orderId, usageById.size() + usageByName.size(), usedSnapshot);
    }

    // ─── INGREDIENT AVAILABILITY CHECK ─────────────────────────────

    /**
     * P0.13: Check whether all ingredients needed for an order are in stock.
     * Uses the ingredient snapshot from OrderItem (frozen recipe at order time),
     * falling back to the live recipe for pre-snapshot orders.
     *
     * @throws IllegalArgumentException with details about which ingredients are short
     */
    public void checkIngredientAvailability(String orderId, String restaurantId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<String, BigDecimal> requiredById = new LinkedHashMap<>();
        Map<String, BigDecimal> requiredByName = new LinkedHashMap<>();

        for (OrderItem item : items) {
            List<Map<String, Object>> snapshot = parseIngredientSnapshot(item.getIngredientSnapshot());
            if (!snapshot.isEmpty()) {
                for (Map<String, Object> entry : snapshot) {
                    String ingredientId = entry.get("ingredientId") != null ? entry.get("ingredientId").toString() : null;
                    String name = entry.get("name") != null ? entry.get("name").toString() : null;
                    BigDecimal qtyPerUnit = entry.get("quantity") != null
                            ? new BigDecimal(entry.get("quantity").toString())
                            : BigDecimal.ZERO;
                    BigDecimal total = qtyPerUnit.multiply(BigDecimal.valueOf(item.getQuantity()));
                    if (ingredientId != null && !ingredientId.isBlank()) {
                        requiredById.merge(ingredientId, total, BigDecimal::add);
                    } else if (name != null && !name.isBlank()) {
                        requiredByName.merge(name, total, BigDecimal::add);
                    }
                }
            } else {
                List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(item.getMenuItemId());
                for (MenuItemIngredient ing : ings) {
                    BigDecimal total = ing.getQuantityPerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
                    if (ing.getIngredientId() != null && !ing.getIngredientId().isBlank()) {
                        requiredById.merge(ing.getIngredientId(), total, BigDecimal::add);
                    } else {
                        requiredByName.merge(ing.getName(), total, BigDecimal::add);
                    }
                }
            }
        }

        List<Map<String, Object>> shortages = new ArrayList<>();

        // Check ID-based ingredients
        for (Map.Entry<String, BigDecimal> entry : requiredById.entrySet()) {
            String ingredientId = entry.getKey();
            BigDecimal required = entry.getValue();
            ingredientRepository.findById(ingredientId).ifPresent(ing -> {
                BigDecimal available = ing.getStockQuantity();
                if (available.compareTo(required) < 0) {
                    Map<String, Object> shortage = new LinkedHashMap<>();
                    shortage.put("ingredientId", ingredientId);
                    shortage.put("name", ing.getName());
                    shortage.put("required", required.setScale(2, RoundingMode.HALF_UP));
                    shortage.put("available", available.setScale(2, RoundingMode.HALF_UP));
                    shortage.put("shortfall", required.subtract(available).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
                    synchronized (shortages) { shortages.add(shortage); }
                }
            });
        }

        // Check name-based ingredients
        for (Map.Entry<String, BigDecimal> entry : requiredByName.entrySet()) {
            String name = entry.getKey();
            BigDecimal required = entry.getValue();
            ingredientRepository.findByRestaurantIdAndName(restaurantId, name).ifPresent(ing -> {
                BigDecimal available = ing.getStockQuantity();
                if (available.compareTo(required) < 0) {
                    Map<String, Object> shortage = new LinkedHashMap<>();
                    shortage.put("name", name);
                    shortage.put("required", required.setScale(2, RoundingMode.HALF_UP));
                    shortage.put("available", available.setScale(2, RoundingMode.HALF_UP));
                    shortage.put("shortfall", required.subtract(available).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
                    synchronized (shortages) { shortages.add(shortage); }
                }
            });
        }

        if (!shortages.isEmpty()) {
            StringBuilder msg = new StringBuilder("Insufficient ingredient stock for order:");
            for (Map<String, Object> s : shortages) {
                msg.append(String.format("\n  - %s: need %s %s, have %s %s",
                        s.get("name"), s.get("required"),
                        s.containsKey("unit") ? s.get("unit") : "",
                        s.get("available"), s.containsKey("unit") ? s.get("unit") : ""));
            }
            throw new IllegalArgumentException(msg.toString());
        }
    }

    // ─── EXISTING BUSINESS LOGIC (preserved) ──────────────────────

    /**
     * Deduct raw ingredient stock for every line item in an order.
     *
     * P0.13: Uses the ingredient snapshot stored on each OrderItem to deduct
     * exactly what was in the recipe when the customer placed the order.
     * Falls back to the live recipe for orders placed before snapshots existed.
     */
    @Transactional
    public void deductForOrder(String orderId, String restaurantId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<String, BigDecimal> usageById = new LinkedHashMap<>();
        Map<String, BigDecimal> usageByName = new LinkedHashMap<>();
        boolean usedSnapshot = false;

        for (OrderItem item : items) {
            List<Map<String, Object>> snapshot = parseIngredientSnapshot(item.getIngredientSnapshot());
            if (!snapshot.isEmpty()) {
                // Use the frozen recipe from order time
                usedSnapshot = true;
                for (Map<String, Object> entry : snapshot) {
                    String ingredientId = entry.get("ingredientId") != null ? entry.get("ingredientId").toString() : null;
                    String name = entry.get("name") != null ? entry.get("name").toString() : null;
                    BigDecimal qtyPerUnit = entry.get("quantity") != null
                            ? new BigDecimal(entry.get("quantity").toString())
                            : BigDecimal.ZERO;
                    BigDecimal total = qtyPerUnit.multiply(BigDecimal.valueOf(item.getQuantity()));

                    if (ingredientId != null && !ingredientId.isBlank()) {
                        usageById.merge(ingredientId, total, BigDecimal::add);
                    } else if (name != null && !name.isBlank()) {
                        usageByName.merge(name, total, BigDecimal::add);
                    }
                }
            } else {
                // Fallback: query live recipe (pre-snapshot orders)
                List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(item.getMenuItemId());
                for (MenuItemIngredient ing : ings) {
                    BigDecimal total = ing.getQuantityPerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
                    if (ing.getIngredientId() != null && !ing.getIngredientId().isBlank()) {
                        usageById.merge(ing.getIngredientId(), total, BigDecimal::add);
                    } else {
                        usageByName.merge(ing.getName(), total, BigDecimal::add);
                    }
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

        // Process name-based deductions (snapshot or legacy fallback)
        for (Map.Entry<String, BigDecimal> entry : usageByName.entrySet()) {
            String name = entry.getKey();
            ingredientRepository.findByRestaurantIdAndName(restaurantId, name).ifPresent(ing -> {
                BigDecimal delta = entry.getValue().negate();
                ing.setStockQuantity(ing.getStockQuantity().add(delta).max(BigDecimal.ZERO));
                ingredientRepository.save(ing);
                auditAndAlert(ing, delta, orderId, restaurantId);
            });
        }

        log.info("Deducted ingredients for order {} ({} id-based, {} name-based, snapshot={})",
                orderId, usageById.size(), usageByName.size(), usedSnapshot);
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

    // ─── POST-DEDUCTION STOCK-OUT CHECK ──────────────────────────

    /**
     * P0.13: After stock has been deducted for an order, check if any ingredients
     * are now fully depleted (stock == 0). Returns the list of depleted ingredients
     * so the caller can notify the kitchen / auto-decline the order.
     *
     * Uses the ingredient snapshot from OrderItem (frozen recipe at order time),
     * falling back to the live recipe for pre-snapshot orders.
     */
    public List<Map<String, Object>> notifyDepletedIngredients(String orderId, String restaurantId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<String, BigDecimal> requiredById = new LinkedHashMap<>();
        Map<String, BigDecimal> requiredByName = new LinkedHashMap<>();

        for (OrderItem item : items) {
            List<Map<String, Object>> snapshot = parseIngredientSnapshot(item.getIngredientSnapshot());
            if (!snapshot.isEmpty()) {
                for (Map<String, Object> entry : snapshot) {
                    String ingredientId = entry.get("ingredientId") != null ? entry.get("ingredientId").toString() : null;
                    String name = entry.get("name") != null ? entry.get("name").toString() : null;
                    BigDecimal qtyPerUnit = entry.get("quantity") != null
                            ? new BigDecimal(entry.get("quantity").toString())
                            : BigDecimal.ZERO;
                    BigDecimal total = qtyPerUnit.multiply(BigDecimal.valueOf(item.getQuantity()));
                    if (ingredientId != null && !ingredientId.isBlank()) {
                        requiredById.merge(ingredientId, total, BigDecimal::add);
                    } else if (name != null && !name.isBlank()) {
                        requiredByName.merge(name, total, BigDecimal::add);
                    }
                }
            } else {
                List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(item.getMenuItemId());
                for (MenuItemIngredient ing : ings) {
                    BigDecimal total = ing.getQuantityPerUnit().multiply(BigDecimal.valueOf(item.getQuantity()));
                    if (ing.getIngredientId() != null && !ing.getIngredientId().isBlank()) {
                        requiredById.merge(ing.getIngredientId(), total, BigDecimal::add);
                    } else {
                        requiredByName.merge(ing.getName(), total, BigDecimal::add);
                    }
                }
            }
        }

        List<Map<String, Object>> depleted = new ArrayList<>();

        // Check ID-based ingredients
        for (Map.Entry<String, BigDecimal> entry : requiredById.entrySet()) {
            String ingredientId = entry.getKey();
            ingredientRepository.findById(ingredientId).ifPresent(ing -> {
                if (ing.getStockQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ingredientId", ingredientId);
                    item.put("name", ing.getName());
                    item.put("stockQuantity", ing.getStockQuantity());
                    item.put("reorderLevel", ing.getReorderLevel());
                    synchronized (depleted) { depleted.add(item); }
                }
            });
        }

        // Check name-based ingredients
        for (Map.Entry<String, BigDecimal> entry : requiredByName.entrySet()) {
            String name = entry.getKey();
            ingredientRepository.findByRestaurantIdAndName(restaurantId, name).ifPresent(ing -> {
                if (ing.getStockQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    item.put("stockQuantity", ing.getStockQuantity());
                    item.put("reorderLevel", ing.getReorderLevel());
                    synchronized (depleted) { depleted.add(item); }
                }
            });
        }

        if (!depleted.isEmpty()) {
            // Build a human-readable list of depleted ingredients
            String ingredientList = depleted.stream()
                    .map(d -> (String) d.get("name"))
                    .collect(Collectors.joining(", "));

            log.warn("Ingredient stock depleted after deducting for order {}: {}", orderId, ingredientList);

            // Emit a high-priority outbox event so the kitchen is notified immediately
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", orderId);
            payload.put("restaurantId", restaurantId);
            payload.put("depletedIngredients", depleted);
            payload.put("message", "Ingredient stock depleted during preparation: " + ingredientList);
            outboxService.record(orderId, "order.ingredient.depleted", payload);
        }

        return depleted;
    }

    // ─── POST-DEDUCTION LOW-STOCK WARNING ─────────────────────────

    /**
     * P0.13: After stock has been deducted for an order, check if any ingredients
     * are running low (stock > 0 but at or below the reorder level). These are
     * ingredients the kitchen should be aware of before they hit zero.
     *
     * Returns the list of low-stock ingredients with current stock and reorder
     * level so the caller can push a real-time warning to the kitchen.
     *
     * Uses the ingredient snapshot from OrderItem (frozen recipe at order time),
     * falling back to the live recipe for pre-snapshot orders.
     */
    public List<Map<String, Object>> notifyLowStockIngredients(String orderId, String restaurantId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Set<String> usedIngredientIds = new LinkedHashSet<>();
        Set<String> usedIngredientNames = new LinkedHashSet<>();

        for (OrderItem item : items) {
            List<Map<String, Object>> snapshot = parseIngredientSnapshot(item.getIngredientSnapshot());
            if (!snapshot.isEmpty()) {
                for (Map<String, Object> entry : snapshot) {
                    String ingredientId = entry.get("ingredientId") != null ? entry.get("ingredientId").toString() : null;
                    String name = entry.get("name") != null ? entry.get("name").toString() : null;
                    if (ingredientId != null && !ingredientId.isBlank()) usedIngredientIds.add(ingredientId);
                    else if (name != null && !name.isBlank()) usedIngredientNames.add(name);
                }
            } else {
                List<MenuItemIngredient> ings = menuItemIngredientRepository.findByMenuItemId(item.getMenuItemId());
                for (MenuItemIngredient ing : ings) {
                    if (ing.getIngredientId() != null && !ing.getIngredientId().isBlank()) usedIngredientIds.add(ing.getIngredientId());
                    else usedIngredientNames.add(ing.getName());
                }
            }
        }

        List<Map<String, Object>> lowStock = new ArrayList<>();

        // Check ID-based ingredients
        for (String ingredientId : usedIngredientIds) {
            ingredientRepository.findById(ingredientId).ifPresent(ing -> {
                BigDecimal stock = ing.getStockQuantity();
                BigDecimal threshold = ing.getLowStockThreshold() != null
                        ? ing.getLowStockThreshold() : ing.getReorderLevel();
                if (stock.compareTo(BigDecimal.ZERO) > 0
                        && threshold != null && stock.compareTo(threshold) <= 0) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("ingredientId", ingredientId);
                    entry.put("name", ing.getName());
                    entry.put("stockQuantity", stock.setScale(2, RoundingMode.HALF_UP));
                    entry.put("lowStockThreshold", threshold.setScale(2, RoundingMode.HALF_UP));
                    entry.put("reorderLevel", ing.getReorderLevel() != null
                            ? ing.getReorderLevel().setScale(2, RoundingMode.HALF_UP) : null);
                    entry.put("unit", ing.getUnit());
                    entry.put("severity", "LOW");
                    synchronized (lowStock) { lowStock.add(entry); }
                }
            });
        }

        // Check name-based ingredients
        for (String name : usedIngredientNames) {
            ingredientRepository.findByRestaurantIdAndName(restaurantId, name).ifPresent(ing -> {
                BigDecimal stock = ing.getStockQuantity();
                BigDecimal threshold = ing.getLowStockThreshold() != null
                        ? ing.getLowStockThreshold() : ing.getReorderLevel();
                if (stock.compareTo(BigDecimal.ZERO) > 0
                        && threshold != null && stock.compareTo(threshold) <= 0) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", name);
                    entry.put("stockQuantity", stock.setScale(2, RoundingMode.HALF_UP));
                    entry.put("lowStockThreshold", threshold.setScale(2, RoundingMode.HALF_UP));
                    entry.put("reorderLevel", ing.getReorderLevel() != null
                            ? ing.getReorderLevel().setScale(2, RoundingMode.HALF_UP) : null);
                    entry.put("unit", ing.getUnit());
                    entry.put("severity", "LOW");
                    synchronized (lowStock) { lowStock.add(entry); }
                }
            });
        }

        if (!lowStock.isEmpty()) {
            String ingredientList = lowStock.stream()
                    .map(d -> d.get("name") + " (" + d.get("stockQuantity") + " " + d.get("unit") + " left)")
                    .collect(Collectors.joining(", "));

            log.warn("Ingredient stock running low after order {}: {}", orderId, ingredientList);

            // Emit outbox event so the kitchen dashboard and email alerts pick it up
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", orderId);
            payload.put("restaurantId", restaurantId);
            payload.put("lowStockIngredients", lowStock);
            payload.put("message", "Ingredient stock running low: " + ingredientList);
            outboxService.record(orderId, "order.ingredient.low_stock", payload);
        }

        return lowStock;
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
