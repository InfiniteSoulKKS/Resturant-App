package com.savorystay.controller;

import com.savorystay.dto.CreateRestaurantRequest;
import com.savorystay.dto.RestaurantResponse;
import com.savorystay.dto.RestaurantSettingsResponse;
import com.savorystay.dto.UpdateRestaurantRequest;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.Restaurant;
import com.savorystay.entity.RestaurantSettings;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.RestaurantSettingsRepository;
import com.savorystay.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final RestaurantSettingsRepository restaurantSettingsRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;

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

    /**
     * Public: restaurant table/time-slot configuration for the checkout modal.
     * Returns defaults if no settings row exists yet.
     */
    @GetMapping("/api/v1/restaurants/{id}/settings")
    public ResponseEntity<?> getSettings(@PathVariable String id) {
        RestaurantSettings settings = restaurantSettingsRepository.findByRestaurantId(id)
                .orElseGet(() -> RestaurantSettings.builder()
                        .restaurantId(id)
                        .build());
        return ResponseEntity.ok(Map.of("success", true, "settings", RestaurantSettingsResponse.from(settings)));
    }

    /**
     * Public: table availability for a specific date + time slot.
     * Returns each table type with its total count and how many are still free.
     * Query params: ?date=YYYY-MM-DD&timeSlot=12:00 PM
     */
    @GetMapping("/api/v1/restaurants/{id}/table-availability")
    public ResponseEntity<?> getTableAvailability(
            @PathVariable String id,
            @RequestParam String date,
            @RequestParam String timeSlot) {

        RestaurantSettings settings = restaurantSettingsRepository.findByRestaurantId(id)
                .orElseGet(() -> RestaurantSettings.builder().restaurantId(id).build());

        // Parse table config
        List<Map<String, Object>> tableTypes = RestaurantSettingsResponse.parseTableConfig(settings.getTableConfig());

        // Count existing DINE_IN orders for this date+slot and overlapping slots
        // Tables are blocked for 1 hour after booking to prevent double-booking
        List<String> timeSlotPrefixes = getTimeSlotPrefixesWithinOneHour(date, timeSlot);
        List<Object[]> booked = orderRepository.countDineInByTimeSlots(id, timeSlotPrefixes);
        Map<Integer, Long> bookedByGuests = new HashMap<>();
        for (Object[] row : booked) {
            Integer guests = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            bookedByGuests.put(guests, count);
        }

        // Build response: for each table type, compute remaining = total - booked
        List<Map<String, Object>> availability = new ArrayList<>();
        for (Map<String, Object> tt : tableTypes) {
            String type = (String) tt.get("type");
            int total = tt.get("count") != null ? ((Number) tt.get("count")).intValue() : 0;
            int guests = extractGuestsFromType(type);
            long bookedCount = bookedByGuests.getOrDefault(guests, 0L);
            int remaining = Math.max(0, total - (int) bookedCount);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("total", total);
            entry.put("booked", (int) bookedCount);
            entry.put("remaining", remaining);
            availability.add(entry);
        }

        return ResponseEntity.ok(Map.of("success", true, "date", date, "timeSlot", timeSlot, "tables", availability));
    }

    /**
     * Public: daily plate availability for all menu items of a restaurant.
     * Query param: ?date=YYYY-MM-DD (defaults to today)
     * Returns each dish with dailyPlateCount (null=unlimited) and platesOrdered today.
     */
    @GetMapping("/api/v1/restaurants/{id}/plate-availability")
    public ResponseEntity<?> getPlateAvailability(
            @PathVariable String id,
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime dayStart = targetDate.atStartOfDay();
        LocalDateTime dayEnd = targetDate.plusDays(1).atStartOfDay();

        List<MenuItem> menuItems = menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(id);
        List<Map<String, Object>> result = new ArrayList<>();

        for (MenuItem item : menuItems) {
            Integer dailyCap = item.getDailyPlateCount();
            long ordered = (dailyCap != null)
                    ? orderItemRepository.countPlatesOrderedForItem(item.getId(), dayStart, dayEnd)
                    : 0;
            int remaining = (dailyCap != null) ? Math.max(0, dailyCap - (int) ordered) : -1; // -1 = unlimited
            boolean available = dailyCap == null || remaining > 0;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("menuItemId", item.getId());
            entry.put("title", item.getTitle());
            entry.put("dailyPlateCount", dailyCap);
            entry.put("platesOrdered", ordered);
            entry.put("remaining", remaining);
            entry.put("available", available);
            result.add(entry);
        }

        return ResponseEntity.ok(Map.of("success", true, "date", targetDate.toString(), "items", result));
    }

    /** Extract the numeric guest count from a type label like "4-Seater". */
    private int extractGuestsFromType(String type) {
        if (type == null) return 2;
        String digits = type.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 2 : Integer.parseInt(digits);
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

    /**
     * Generate time slot prefixes for a 1-hour window around the requested slot.
     * For example, if the requested slot is "12:00 PM" and slots are every 30 min,
     * returns ["2026-08-22 12:00 PM", "2026-08-22 12:30 PM"] (both blocked for 1 hour).
     * This prevents double-booking of the same table.
     */
    private List<String> getTimeSlotPrefixesWithinOneHour(String date, String timeSlot) {
        List<String> prefixes = new ArrayList<>();
        try {
            // Parse time slot: "12:00 PM" → LocalTime
            DateTimeFormatter fmt12 = DateTimeFormatter.ofPattern("h:mm a");
            LocalTime requestedTime = LocalTime.parse(timeSlot.trim(), fmt12);
            String datePrefix = date + " " + timeSlot.trim();
            prefixes.add(datePrefix);

            // Add next slots within 1 hour (assuming 30-min slot intervals)
            for (int i = 1; i <= 2; i++) {
                LocalTime next = requestedTime.plusMinutes(i * 30L);
                String nextSlot = next.format(fmt12);
                prefixes.add(date + " " + nextSlot);
            }
        } catch (Exception e) {
            // Fallback: just use the exact slot
            prefixes.add(date + " " + timeSlot.trim());
        }
        return prefixes;
    }
}
