package com.savorystay.controller;

import com.savorystay.dto.CreateRestaurantRequest;
import com.savorystay.dto.RestaurantResponse;
import com.savorystay.dto.UpdateRestaurantRequest;
import com.savorystay.entity.Restaurant;
import com.savorystay.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RestaurantController {

    private final RestaurantService restaurantService;

    /**
     * Public: all active restaurants for customer browsing.
     */
    @GetMapping("/api/v1/restaurants")
    public ResponseEntity<?> listActive() {
        List<Restaurant> active = restaurantService.listActive();
        List<RestaurantResponse> dtos = active.stream().map(RestaurantResponse::from).toList();
        return ResponseEntity.ok(Map.of("success", true, "restaurants", dtos));
    }

    /**
     * Public: fetch a single restaurant.
     * Suspended restaurants are hidden from the public API (404 = offline).
     */
    @GetMapping("/api/v1/restaurants/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return restaurantService.get(id)
                .filter(r -> "ACTIVE".equals(r.getStatus()))
                .map(r -> ResponseEntity.ok(Map.of("success", true, "restaurant", RestaurantResponse.from(r))))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("success", false, "message", "Restaurant not found")));
    }

    // ==================== SUPER ADMIN ====================

    /**
     * Super Admin: create a restaurant with its first admin account.
     */
    @PostMapping("/api/v1/super-admin/restaurants")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateRestaurantRequest req) {
        try {
            Restaurant r = Restaurant.builder()
                    .name(req.name())
                    .description(req.description())
                    .address(req.address())
                    .city(req.city())
                    .cuisine(req.cuisine())
                    .phone(req.phone())
                    .email(req.email())
                    .logoUrl(req.logoUrl())
                    .currency(req.currency() != null ? req.currency() : "INR")
                    .build();

            Restaurant saved = restaurantService.createRestaurant(
                    r,
                    req.adminUsername(),
                    req.adminEmail(),
                    req.adminPassword());

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("restaurant", RestaurantResponse.from(saved));
            resp.put("message", "Restaurant created with admin account");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error creating restaurant: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Super Admin: list all restaurants (including suspended).
     */
    @GetMapping("/api/v1/super-admin/restaurants")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> listAll() {
        List<RestaurantResponse> dtos = restaurantService.listAll().stream().map(RestaurantResponse::from).toList();
        return ResponseEntity.ok(Map.of("success", true, "restaurants", dtos));
    }

    @PutMapping("/api/v1/super-admin/restaurants/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody UpdateRestaurantRequest updates) {
        try {
            Restaurant entity = Restaurant.builder()
                    .name(updates.name())
                    .slug(updates.slug())
                    .description(updates.description())
                    .address(updates.address())
                    .city(updates.city())
                    .cuisine(updates.cuisine())
                    .phone(updates.phone())
                    .email(updates.email())
                    .logoUrl(updates.logoUrl())
                    .status(updates.status())
                    .currency(updates.currency())
                    .build();
            Restaurant saved = restaurantService.updateRestaurant(id, entity);
            return ResponseEntity.ok(Map.of("success", true, "restaurant", RestaurantResponse.from(saved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/v1/super-admin/restaurants/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            restaurantService.deleteRestaurant(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Restaurant deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
