package com.savorystay.service;

import com.savorystay.dto.OperatingHourRequest;
import com.savorystay.entity.DishAvailability;
import com.savorystay.entity.DishSlotOverride;
import com.savorystay.entity.PreOrderSettings;
import com.savorystay.entity.RestaurantOperatingHour;
import com.savorystay.repository.DishAvailabilityRepository;
import com.savorystay.repository.DishSlotOverrideRepository;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.PreOrderSettingsRepository;
import com.savorystay.repository.RestaurantOperatingHourRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Manager/admin CRUD for pre-order configuration (hours, settings, availability, slots). */
@Service
@RequiredArgsConstructor
public class PreOrderConfigService {

    private final RestaurantOperatingHourRepository operatingHourRepository;
    private final PreOrderSettingsRepository settingsRepository;
    private final DishAvailabilityRepository availabilityRepository;
    private final DishSlotOverrideRepository overrideRepository;
    private final MenuItemRepository menuItemRepository;

    // ------------------------------------------------------------------
    // Operating hours
    // ------------------------------------------------------------------

    public List<RestaurantOperatingHour> operatingHours(String restaurantId) {
        List<RestaurantOperatingHour> hours = operatingHourRepository.findByRestaurantId(restaurantId);
        hours.sort(Comparator.comparing(RestaurantOperatingHour::getDayOfWeek));
        return hours;
    }

    /** Upserts one day's operating hours (partial updates supported). */
    @Transactional
    public RestaurantOperatingHour upsertOperatingHour(String restaurantId, OperatingHourRequest req) {
        Optional<RestaurantOperatingHour> existing =
                operatingHourRepository.findByRestaurantIdAndDayOfWeek(restaurantId, req.dayOfWeek());
        RestaurantOperatingHour h = existing.orElseGet(() -> RestaurantOperatingHour.builder()
                .restaurantId(restaurantId)
                .dayOfWeek(req.dayOfWeek())
                .openTime(PreOrderAvailabilityService.DEFAULT_OPEN_TIME)
                .closeTime(PreOrderAvailabilityService.DEFAULT_CLOSE_TIME)
                .closed(false)
                .build());
        if (req.openTime() != null) h.setOpenTime(req.openTime());
        if (req.closeTime() != null) h.setCloseTime(req.closeTime());
        if (req.closed() != null) h.setClosed(req.closed());
        validateWindow(h);
        validateCutoffNotAfterHours(restaurantId, h);
        return operatingHourRepository.save(h);
    }

    private void validateWindow(RestaurantOperatingHour h) {
        if (Boolean.TRUE.equals(h.getClosed())) return;
        if (h.getOpenTime() == null || h.getCloseTime() == null) {
            throw new IllegalArgumentException("openTime and closeTime are required when the day is not closed");
        }
        if (!h.getOpenTime().isBefore(h.getCloseTime())) {
            throw new IllegalArgumentException("openTime must be before closeTime");
        }
    }

    // ------------------------------------------------------------------
    // Pre-order settings
    // ------------------------------------------------------------------

    @Transactional
    public PreOrderSettings updateSettings(String restaurantId, LocalTime cutoffTime, Integer advanceDays) {
        PreOrderSettings s = settingsRepository.findByRestaurantId(restaurantId).orElseGet(() ->
                PreOrderSettings.builder()
                        .restaurantId(restaurantId)
                        .cutoffTime(PreOrderAvailabilityService.DEFAULT_CUTOFF_TIME)
                        .advanceDays(PreOrderAvailabilityService.DEFAULT_ADVANCE_DAYS)
                        .build());
        if (cutoffTime != null) {
            validateCutoffNotAfterOpening(restaurantId, cutoffTime);
            s.setCutoffTime(cutoffTime);
        }
        if (advanceDays != null) s.setAdvanceDays(advanceDays);
        return settingsRepository.save(s);
    }

    /**
     * A pre-order cutoff must never be after the restaurant's opening time on
     * any open day: the cutoff for date D falls on D-1, which can be any
     * weekday, so a single cutoff must fit every open day. Closed days impose
     * no constraint (the restaurant never opens).
     */
    private void validateCutoffNotAfterOpening(String restaurantId, LocalTime cutoff) {
        List<String> conflicts = new ArrayList<>();
        for (RestaurantOperatingHour h : operatingHourRepository.findByRestaurantId(restaurantId)) {
            if (Boolean.TRUE.equals(h.getClosed()) || h.getOpenTime() == null) continue;
            if (cutoff.isAfter(h.getOpenTime())) {
                conflicts.add(PreOrderAvailabilityService.weekdayLabel(h.getDayOfWeek())
                        + " (opens " + PreOrderAvailabilityService.formatTime(h.getOpenTime()) + ")");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pre-order cutoff (" + PreOrderAvailabilityService.formatTime(cutoff)
                            + ") cannot be after the restaurant opening time on: "
                            + String.join(", ", conflicts)
                            + ". Set the cutoff at or before opening time.");
        }
    }

    /**
     * When operating hours change, the stored cutoff must still satisfy the
     * same rule (not after that day's opening time) — otherwise the manager
     * would silently end up with an invalid configuration.
     */
    private void validateCutoffNotAfterHours(String restaurantId, RestaurantOperatingHour h) {
        if (Boolean.TRUE.equals(h.getClosed()) || h.getOpenTime() == null) return;
        settingsRepository.findByRestaurantId(restaurantId).ifPresent(s -> {
            if (s.getCutoffTime().isAfter(h.getOpenTime())) {
                throw new IllegalArgumentException(
                        "Pre-order cutoff (" + PreOrderAvailabilityService.formatTime(s.getCutoffTime())
                                + ") is after this day's opening time ("
                                + PreOrderAvailabilityService.formatTime(h.getOpenTime())
                                + "). Lower the cutoff in Cutoff & Horizon first.");
            }
        });
    }

    // ------------------------------------------------------------------
    // Dish availability (weekly)
    // ------------------------------------------------------------------

    /** Replaces a dish's weekly availability with the given weekday list. */
    @Transactional
    public List<DishAvailability> setDishAvailability(String restaurantId, String menuItemId,
                                                      List<Integer> days) {
        menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        // Bulk delete (executes immediately) so the subsequent inserts in this
        // transaction cannot collide with the unique key (menu_item_id, day_of_week).
        availabilityRepository.deleteAllByMenuItemId(menuItemId);
        List<DishAvailability> rows = new ArrayList<>();
        for (Integer day : days) {
            rows.add(DishAvailability.builder()
                    .restaurantId(restaurantId)
                    .menuItemId(menuItemId)
                    .dayOfWeek(day)
                    .build());
        }
        return availabilityRepository.saveAll(rows);
    }

    /** Weekly days + explicit overrides for a dish (manager view). */
    public Map<String, Object> dishAvailabilityView(String restaurantId, String menuItemId) {
        menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        List<Integer> days = availabilityRepository.findByMenuItemId(menuItemId).stream()
                .map(DishAvailability::getDayOfWeek)
                .sorted()
                .toList();
        List<Map<String, Object>> overrides = overrideRepository.findByMenuItemId(menuItemId).stream()
                .sorted(Comparator.comparing(DishSlotOverride::getTargetDate))
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", o.getTargetDate().toString());
                    m.put("action", o.getAction());
                    return m;
                })
                .collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menuItemId", menuItemId);
        result.put("days", days);
        result.put("overrides", overrides);
        return result;
    }

    // ------------------------------------------------------------------
    // Slot overrides (specific dates)
    // ------------------------------------------------------------------

    @Transactional
    public DishSlotOverride upsertSlotOverride(String restaurantId, String menuItemId,
                                               LocalDate date, String action) {
        menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        Optional<DishSlotOverride> existing = overrideRepository.findByMenuItemIdAndTargetDate(menuItemId, date);
        DishSlotOverride o = existing.orElseGet(() -> DishSlotOverride.builder()
                .restaurantId(restaurantId)
                .menuItemId(menuItemId)
                .targetDate(date)
                .build());
        o.setAction(action);
        return overrideRepository.save(o);
    }

    @Transactional
    public void clearSlotOverride(String restaurantId, String menuItemId, LocalDate date) {
        menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found in this restaurant"));
        overrideRepository.deleteByMenuItemIdAndTargetDate(menuItemId, date);
    }
}
