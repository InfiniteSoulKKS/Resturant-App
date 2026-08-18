package com.savorystay.controller;

import com.savorystay.dto.CreateIngredientRequest;
import com.savorystay.dto.IngredientResponse;
import com.savorystay.dto.UpdateIngredientRequest;
import com.savorystay.entity.Ingredient;
import com.savorystay.service.IngredientService;
import com.savorystay.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ingredients")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IngredientController {

    private final IngredientService ingredientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> list(@RequestParam(required = false) String restaurantId,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<Ingredient> list;
        if (q != null && !q.isBlank()) {
            list = includeInactive
                    ? ingredientService.searchAll(restaurantId, q)
                    : ingredientService.search(restaurantId, q);
        } else {
            list = includeInactive
                    ? ingredientService.list(restaurantId)
                    : ingredientService.listActive(restaurantId);
        }
        List<IngredientResponse> dtos = list.stream().map(IngredientResponse::from).toList();
        return ResponseEntity.ok(Map.of("success", true, "ingredients", dtos));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> get(@PathVariable String id,
                                 @RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        return ingredientService.list(restaurantId).stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .map(i -> ResponseEntity.ok((Object) Map.of("success", true, "ingredient", IngredientResponse.from(i))))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Ingredient not found")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateIngredientRequest req,
                                    @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            Ingredient ingredient = Ingredient.builder()
                    .name(req.name())
                    .displayName(req.displayName())
                    .unit(req.unit())
                    .category(req.category())
                    .stockQuantity(req.stockQuantity())
                    .reorderLevel(req.reorderLevel())
                    .build();
            Ingredient saved = ingredientService.create(restaurantId, ingredient);

            // Include similar ingredients in the response for the "Did you mean?" UI
            List<Ingredient> similar = ingredientService.findSimilar(restaurantId, req.name());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ingredient", IngredientResponse.from(saved));
            if (!similar.isEmpty()) {
                response.put("similarIngredients", similar.stream().map(IngredientResponse::from).toList());
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating ingredient: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody UpdateIngredientRequest req,
                                    @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            Ingredient ingredient = Ingredient.builder()
                    .name(req.name())
                    .displayName(req.displayName())
                    .unit(req.unit())
                    .category(req.category())
                    .stockQuantity(req.stockQuantity())
                    .reorderLevel(req.reorderLevel())
                    .active(req.active())
                    .build();
            Ingredient saved = ingredientService.update(restaurantId, id, ingredient);
            return ResponseEntity.ok(Map.of("success", true, "ingredient", IngredientResponse.from(saved)));
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).body(Map.of("success", false, "message", "Stock changed concurrently. Please retry."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating ingredient: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Soft-delete: deactivate ingredient (preserves history). */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> deactivate(@PathVariable String id,
                                        @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            Ingredient saved = ingredientService.deactivate(restaurantId, id);
            return ResponseEntity.ok(Map.of("success", true, "ingredient", IngredientResponse.from(saved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Reactivate a previously deactivated ingredient. */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> reactivate(@PathVariable String id,
                                        @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            Ingredient saved = ingredientService.reactivate(restaurantId, id);
            return ResponseEntity.ok(Map.of("success", true, "ingredient", IngredientResponse.from(saved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Hard delete — only if no recipe/inventory references. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable String id,
                                    @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            ingredientService.delete(restaurantId, id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Ingredient deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Get usage count for an ingredient — how many recipes reference it. */
    @GetMapping("/{id}/usage")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> usage(@PathVariable String id) {
        long count = ingredientService.getUsageCount(id);
        return ResponseEntity.ok(Map.of("success", true, "usageCount", count));
    }

    /** Search for similar ingredient names — for "Did you mean?" suggestions. */
    @GetMapping("/similar")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> similar(@RequestParam String name,
                                     @RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<Ingredient> similar = ingredientService.findSimilar(restaurantId, name);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "similarIngredients", similar.stream().map(IngredientResponse::from).toList()
        ));
    }

    @GetMapping("/forecast")
    @PreAuthorize("hasAnyRole('CHEF','MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> forecast(@RequestParam(required = false) String date,
                                      @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            LocalDate targetDate = date != null ? LocalDate.parse(date) : null;
            Map<String, Object> forecast = ingredientService.forecastForDate(restaurantId, targetDate);
            Map<String, Object> resp = new HashMap<>(forecast);
            resp.put("success", true);
            resp.put("date", (targetDate != null ? targetDate : LocalDate.now().plusDays(1)).toString());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error computing forecast: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
