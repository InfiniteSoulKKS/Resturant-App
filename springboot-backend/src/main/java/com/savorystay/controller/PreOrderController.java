package com.savorystay.controller;

import com.savorystay.dto.DishAvailabilityRequest;
import com.savorystay.dto.OperatingHourRequest;
import com.savorystay.dto.SlotOverrideRequest;
import com.savorystay.dto.UpdatePreOrderSettingsRequest;
import com.savorystay.entity.DishAvailability;
import com.savorystay.entity.DishSlotOverride;
import com.savorystay.entity.PreOrderSettings;
import com.savorystay.entity.RestaurantOperatingHour;
import com.savorystay.service.PreOrderAvailabilityService;
import com.savorystay.service.PreOrderConfigService;
import com.savorystay.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pre-order configuration (manager/admin) and orderable-date lookup (customer
 * checkout). All business rules live in {@link PreOrderAvailabilityService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pre-orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PreOrderController {

    private final PreOrderConfigService configService;
    private final PreOrderAvailabilityService availabilityService;

    // ------------------------------------------------------------------
    // Customer checkout: which future dates can be pre-ordered
    // ------------------------------------------------------------------

    /**
     * Returns the next {@code daysAhead} days (default = restaurant's advance
     * horizon) with per-date and per-dish availability for the given dishes.
     * Body: { "restaurantId": "REST_DEMO_1", "menuItemIds": ["...", ...],
     *        "daysAhead": 7 (optional) }
     */
    @PostMapping("/dates")
    public ResponseEntity<?> orderableDates(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String restaurantId = body != null && body.get("restaurantId") != null
                    ? String.valueOf(body.get("restaurantId"))
                    : TenantContext.getRestaurantId();
            if (restaurantId == null || restaurantId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "restaurantId is required"));
            }
            @SuppressWarnings("unchecked")
            List<String> menuItemIds = body != null && body.get("menuItemIds") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList()
                    : List.of();
            Integer daysAhead = body != null && body.get("daysAhead") != null
                    ? Integer.valueOf(String.valueOf(body.get("daysAhead"))) : null;

            List<Map<String, Object>> dates = availabilityService.availableDates(restaurantId, menuItemIds, daysAhead);
            return ResponseEntity.ok(Map.of("success", true, "dates", dates));
        } catch (Exception e) {
            log.error("Error computing orderable dates: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Manager: operating hours (weekly)
    // ------------------------------------------------------------------

    @GetMapping("/config/hours")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> operatingHours(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<RestaurantOperatingHour> hours = configService.operatingHours(restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "operatingHours", hours));
    }

    /** Upsert one day's operating hours. Body: { dayOfWeek, openTime, closeTime, closed } */
    @PutMapping("/config/hours")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> upsertOperatingHour(@Valid @RequestBody OperatingHourRequest req,
                                                 @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            RestaurantOperatingHour saved = configService.upsertOperatingHour(restaurantId, req);
            return ResponseEntity.ok(Map.of("success", true, "operatingHour", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Manager: pre-order settings (cutoff + horizon)
    // ------------------------------------------------------------------

    @GetMapping("/config/settings")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> settings(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        PreOrderSettings s = availabilityService.settings(restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "settings", s));
    }

    /** Body: { cutoffTime: "09:00", advanceDays: 7 } */
    @PutMapping("/config/settings")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody UpdatePreOrderSettingsRequest req,
                                            @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            PreOrderSettings saved = configService.updateSettings(restaurantId, req.cutoffTime(), req.advanceDays());
            return ResponseEntity.ok(Map.of("success", true, "settings", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Manager: dish availability (weekly schedule)
    // ------------------------------------------------------------------

    @GetMapping("/menu-items/{menuItemId}/availability")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> dishAvailability(@PathVariable String menuItemId,
                                              @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            return ResponseEntity.ok(Map.of("success", true, "availability", configService.dishAvailabilityView(restaurantId, menuItemId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Body: { days: [1,3,5] } (1=Monday .. 7=Sunday) — replaces the whole weekly schedule. */
    @PutMapping("/menu-items/{menuItemId}/availability")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> setDishAvailability(@PathVariable String menuItemId,
                                                 @Valid @RequestBody DishAvailabilityRequest req,
                                                 @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            List<DishAvailability> saved = configService.setDishAvailability(restaurantId, menuItemId, req.days());
            return ResponseEntity.ok(Map.of("success", true, "availability", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Manager: explicit slot overrides (specific dates)
    // ------------------------------------------------------------------

    /** Open or close a specific date for a dish. Body: { date: "2026-08-14", action: "OPEN" | "CLOSE" } */
    @PutMapping("/menu-items/{menuItemId}/slots")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> upsertSlotOverride(@PathVariable String menuItemId,
                                                @Valid @RequestBody SlotOverrideRequest req,
                                                @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            DishSlotOverride saved = configService.upsertSlotOverride(restaurantId, menuItemId, req.date(), req.action());
            return ResponseEntity.ok(Map.of("success", true, "override", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Remove an override, returning to the weekly schedule. Query param: date=YYYY-MM-DD */
    @DeleteMapping("/menu-items/{menuItemId}/slots")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> clearSlotOverride(@PathVariable String menuItemId,
                                               @RequestParam LocalDate date,
                                               @RequestParam(required = false) String restaurantId) {
        try {
            restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            configService.clearSlotOverride(restaurantId, menuItemId, date);
            return ResponseEntity.ok(Map.of("success", true, "message", "Slot override removed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
