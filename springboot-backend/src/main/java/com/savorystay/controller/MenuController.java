package com.savorystay.controller;

import com.savorystay.dto.CartAvailabilityRequest;
import com.savorystay.dto.CreateMenuItemRequest;
import com.savorystay.dto.MenuItemIngredientResponse;
import com.savorystay.dto.MenuItemResponse;
import com.savorystay.dto.PriceChangeRequest;
import com.savorystay.dto.PriceRuleResponse;
import com.savorystay.dto.UpdateMenuItemRequest;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.service.MenuService;
import com.savorystay.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MenuController {

    private final MenuService menuService;

    /**
     * Public: browse a restaurant's available menu.
     * Suspended restaurants are offline — their menu is not served publicly.
     */
    @GetMapping("/api/v1/restaurants/{id}/menu")
    public ResponseEntity<?> publicMenu(@PathVariable String id) {
        if (!menuService.isRestaurantActive(id)) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Restaurant not found"));
        }
        List<MenuItem> menu = menuService.listMenu(id, true);
        List<MenuItemResponse> dtos = menu.stream().map(MenuItemResponse::from).toList();
        return ResponseEntity.ok(Map.of("success", true, "menuItems", dtos));
    }

    /**
     * Staff: full menu management (all items including sold-out).
     */
    @GetMapping("/api/v1/menu")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> listMenu(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<MenuItem> menu = menuService.listMenu(restaurantId, false);
        List<MenuItemResponse> dtos = menu.stream().map(MenuItemResponse::from).toList();
        return ResponseEntity.ok(Map.of("success", true, "menuItems", dtos));
    }

    @GetMapping("/api/v1/menu/{id}")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> getMenuItem(@PathVariable String id,
                                         @RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        return menuService.get(id, restaurantId)
                .map(item -> ResponseEntity.ok(Map.of("success", true, "menuItem", MenuItemResponse.from(item))))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("success", false, "message", "Menu item not found")));
    }

    @PostMapping("/api/v1/menu")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateMenuItemRequest req) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(req.restaurantId());
            if (restaurantId == null || restaurantId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            MenuItem item = toEntity(req);
            item.setRestaurantId(restaurantId);
            MenuItem saved = menuService.create(item, req.ingredients());
            return ResponseEntity.ok(Map.of("success", true, "menuItem", MenuItemResponse.from(saved)));
        } catch (Exception e) {
            log.error("Error creating menu item: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/api/v1/menu/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody UpdateMenuItemRequest req) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(req.restaurantId());
            if (restaurantId == null || restaurantId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            MenuItem item = toEntity(req);
            item.setRestaurantId(restaurantId);
            MenuItem saved = menuService.update(id, restaurantId, item, req.ingredients());
            return ResponseEntity.ok(Map.of("success", true, "menuItem", MenuItemResponse.from(saved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/v1/menu/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable String id,
                                    @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            menuService.delete(id, restaurantId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Menu item deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Ingredients recipe for a menu item (used by the kitchen).
     * Scoped to the caller's restaurant so staff can never read another
     * restaurant's recipe by guessing an item id.
     */
    @GetMapping("/api/v1/menu/{id}/ingredients")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> ingredients(@PathVariable String id,
                                         @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            List<MenuItemIngredient> ings = menuService.ingredientsFor(id, restaurantId);
            List<MenuItemIngredientResponse> dtos = ings.stream().map(MenuItemIngredientResponse::from).toList();
            return ResponseEntity.ok(Map.of("success", true, "ingredients", dtos));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Schedule a price change (effective immediately or from a future timestamp).
     * Body: { price: 450, effectiveFrom: "2026-08-11T10:00:00" (optional) }
     */
    @PostMapping("/api/v1/menu/{id}/price")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> schedulePrice(@PathVariable String id,
                                           @Valid @RequestBody PriceChangeRequest req,
                                           @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null || restaurantId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            var rule = menuService.schedulePriceChange(id, restaurantId, req.price(), req.effectiveFrom());
            return ResponseEntity.ok(Map.of("success", true, "priceRule", PriceRuleResponse.from(rule)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // SOLD-OUT / 86 FEATURE
    // ------------------------------------------------------------------

    /**
     * Chef/manager marks a dish as sold out (86) or restores it.
     * POST /api/v1/menu/{id}/sold-out  { soldOut: true/false }
     *
     * IMPORTANT: Sold-out does NOT cancel already confirmed orders.
     * Existing confirmed orders remain valid.
     */
    @PostMapping("/api/v1/menu/{id}/sold-out")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> toggleSoldOut(@PathVariable String id,
                                           @RequestBody Map<String, Boolean> body,
                                           @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            Boolean soldOut = body.getOrDefault("soldOut", true);
            MenuItem item = menuService.get(id, restaurantId)
                    .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
            item.setStatus(Boolean.TRUE.equals(soldOut) ? "Sold Out" : "Available");
            MenuItem saved = menuService.updateStatus(id, restaurantId, item.getStatus());
            String statusText = Boolean.TRUE.equals(soldOut) ? "marked as SOLD OUT" : "restored to AVAILABLE";
            return ResponseEntity.ok(Map.of("success", true, "menuItem", MenuItemResponse.from(saved),
                    "message", item.getTitle() + " " + statusText));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // KITCHEN NOTES
    // ------------------------------------------------------------------

    /**
     * Add/update a kitchen note on an order item.
     * POST /api/v1/orders/{orderId}/items/{itemId}/notes  { notes: "Less spicy" }
     * Handled via OrderController — see below.
     */

    // -------- request DTO -> entity mapping --------

    private MenuItem toEntity(CreateMenuItemRequest req) {
        return MenuItem.builder()
                .title(req.title())
                .description(req.description())
                .price(req.price())
                .category(req.category())
                .imageUrl(req.imageUrl())
                .status(req.status() != null ? req.status() : "Available")
                .isVeg(req.isVeg() != null ? req.isVeg() : true)
                .spiceLevel(req.spiceLevel() != null ? req.spiceLevel() : "Medium")
                .prepMinutes(req.prepMinutes())
                .build();
    }

    private MenuItem toEntity(UpdateMenuItemRequest req) {
        return MenuItem.builder()
                .title(req.title())
                .description(req.description())
                .price(req.price())
                .category(req.category())
                .imageUrl(req.imageUrl())
                .status(req.status())
                .isVeg(req.isVeg())
                .spiceLevel(req.spiceLevel())
                .prepMinutes(req.prepMinutes())
                .build();
    }

    // ------------------------------------------------------------------
    // CART AVAILABILITY CHECK
    // ------------------------------------------------------------------

    /**
     * Check whether all items in a customer's cart are still available.
     * Returns a list of unavailable items (empty = all good).
     * This lets the frontend detect sold-out items before order placement.
     */
    @PostMapping("/api/v1/menu/availability-check")
    public ResponseEntity<?> checkCartAvailability(@RequestBody CartAvailabilityRequest req) {
        String restaurantId = TenantContext.resolveRestaurantScope(req.restaurantId());
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        List<Map<String, Object>> unavailable = new java.util.ArrayList<>();
        for (CartAvailabilityRequest.CartItemCheck item : req.items()) {
            var menuItem = menuService.get(item.menuItemId(), restaurantId);
            if (menuItem.isEmpty() || !"Available".equals(menuItem.get().getStatus())) {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("menuItemId", item.menuItemId());
                entry.put("title", menuItem.map(MenuItem::getTitle).orElse("Unknown item"));
                entry.put("status", menuItem.map(MenuItem::getStatus).orElse("NOT_FOUND"));
                entry.put("quantity", item.quantity());
                unavailable.add(entry);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "allAvailable", unavailable.isEmpty(),
                "unavailableItems", unavailable
        ));
    }
}
