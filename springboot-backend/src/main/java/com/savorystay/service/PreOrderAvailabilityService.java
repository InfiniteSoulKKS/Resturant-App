package com.savorystay.service;

import com.savorystay.common.BusinessClock;
import com.savorystay.entity.DishAvailability;
import com.savorystay.entity.DishSlotOverride;
import com.savorystay.entity.MenuItem;
import com.savorystay.entity.PreOrderSettings;
import com.savorystay.entity.RestaurantOperatingHour;
import com.savorystay.repository.DishAvailabilityRepository;
import com.savorystay.repository.DishSlotOverrideRepository;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.PreOrderSettingsRepository;
import com.savorystay.repository.RestaurantOperatingHourRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single source of truth for pre-order business rules:
 *
 * <ul>
 *   <li><b>Cutoff</b> — orders for fulfillment date D close at the configured
 *       cutoff time (default 09:00) on day D-1, evaluated in the business
 *       timezone. Exactly at cutoff = closed.</li>
 *   <li><b>Horizon</b> — pre-orders only for dates between tomorrow and
 *       today + advanceDays (default 7).</li>
 *   <li><b>Closure</b> — a weekly holiday, an invalid/unset window, or a day
 *       closing at/before 14:00 ("2nd half closed") blocks ALL pre-orders for
 *       that day. On open days the requested pickup time must fall inside the
 *       operating window. Same-day PICKUP/DINE_IN are not affected.</li>
 *   <li><b>Dish availability</b> — explicit CLOSE beats explicit OPEN beats the
 *       weekly schedule; a dish with no schedule at all is available every day
 *       (backward compatible). Restaurant closure always wins.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreOrderAvailabilityService {

    public static final LocalTime DEFAULT_CUTOFF_TIME = LocalTime.of(9, 0);
    public static final int DEFAULT_ADVANCE_DAYS = 7;
    public static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(23, 0);

    /** A day closing at/before this time is a "2nd half closed" day → no pre-orders. */
    public static final LocalTime HALF_DAY_CUTOFF = LocalTime.of(14, 0);

    private static final List<String> DAY_NAMES = List.of(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private final RestaurantOperatingHourRepository operatingHourRepository;
    private final PreOrderSettingsRepository settingsRepository;
    private final DishAvailabilityRepository availabilityRepository;
    private final DishSlotOverrideRepository overrideRepository;
    private final MenuItemRepository menuItemRepository;
    private final BusinessClock clock;

    // ------------------------------------------------------------------
    // Configuration lookups (with safe defaults when nothing is set)
    // ------------------------------------------------------------------

    public Map<Integer, RestaurantOperatingHour> hoursByDay(String restaurantId) {
        Map<Integer, RestaurantOperatingHour> map = new HashMap<>();
        for (RestaurantOperatingHour h : operatingHourRepository.findByRestaurantId(restaurantId)) {
            map.put(h.getDayOfWeek(), h);
        }
        return map;
    }

    public PreOrderSettings settings(String restaurantId) {
        return settingsRepository.findByRestaurantId(restaurantId).orElseGet(() ->
                PreOrderSettings.builder()
                        .restaurantId(restaurantId)
                        .cutoffTime(DEFAULT_CUTOFF_TIME)
                        .advanceDays(DEFAULT_ADVANCE_DAYS)
                        .build());
    }

    public Optional<RestaurantOperatingHour> hourFor(String restaurantId, DayOfWeek day) {
        return operatingHourRepository.findByRestaurantIdAndDayOfWeek(restaurantId, day.getValue());
    }

    // ------------------------------------------------------------------
    // Closure rules
    // ------------------------------------------------------------------

    /**
     * True when NO pre-orders are accepted for the given fulfillment date:
     * weekly holiday, unset/invalid window, or a "2nd half closed" day
     * (closes at/before 14:00).
     */
    public boolean isPreOrderBlockedOn(String restaurantId, LocalDate date) {
        RestaurantOperatingHour h = hoursByDay(restaurantId).get(date.getDayOfWeek().getValue());
        if (h == null) return false; // unconfigured → treated as open (manager is reminded to configure)
        if (Boolean.TRUE.equals(h.getClosed())) return true;
        if (h.getOpenTime() == null || h.getCloseTime() == null) return true;
        if (!h.getOpenTime().isBefore(h.getCloseTime())) return true;
        return !h.getCloseTime().isAfter(HALF_DAY_CUTOFF); // closes at/before 14:00
    }

    /** True if the given pickup time falls outside the day's operating window. */
    public boolean isPickupOutsideOpenHours(String restaurantId, LocalDate date, LocalTime pickupTime) {
        if (pickupTime == null) return false;
        RestaurantOperatingHour h = hoursByDay(restaurantId).get(date.getDayOfWeek().getValue());
        if (h == null || h.getOpenTime() == null || h.getCloseTime() == null) return false;
        return pickupTime.isBefore(h.getOpenTime()) || !pickupTime.isBefore(h.getCloseTime());
    }

    // ------------------------------------------------------------------
    // Cutoff rules
    // ------------------------------------------------------------------

    public LocalDateTime cutoffFor(String restaurantId, LocalDate date) {
        PreOrderSettings s = settings(restaurantId);
        return date.minusDays(1).atTime(s.getCutoffTime());
    }

    /** Cutoff has passed when now is AT or AFTER the cutoff instant (business timezone). */
    public boolean isCutoffPassed(String restaurantId, LocalDate date) {
        return !clock.now().isBefore(cutoffFor(restaurantId, date));
    }

    // ------------------------------------------------------------------
    // Dish availability rules
    // ------------------------------------------------------------------

    /**
     * Explicit CLOSE > explicit OPEN > weekly schedule. A dish with no weekly
     * schedule is available every day.
     */
    public boolean isDishAvailable(String restaurantId, String menuItemId, LocalDate date) {
        Optional<DishSlotOverride> override = overrideRepository
                .findByMenuItemIdAndTargetDate(menuItemId, date);
        if (override.isPresent()) {
            return "OPEN".equals(override.get().getAction());
        }
        Set<Integer> days = availabilityRepository.findByMenuItemId(menuItemId).stream()
                .map(DishAvailability::getDayOfWeek)
                .collect(Collectors.toSet());
        if (days.isEmpty()) return true; // not configured → available (backward compatible)
        return days.contains(date.getDayOfWeek().getValue());
    }

    public Set<Integer> weeklyDaysFor(String menuItemId) {
        return availabilityRepository.findByMenuItemId(menuItemId).stream()
                .map(DishAvailability::getDayOfWeek)
                .collect(Collectors.toSet());
    }

    /**
     * True when pre-order availability is NOT configured for the next
     * {@code days} days: either the operating hours are missing/incomplete for
     * any of those days, or no dish has a weekly availability configured at
     * all. Deliberate closures (closed=true / a 2nd-half-close window) are
     * valid configurations and do NOT trigger the reminder.
     */
    public boolean needsPreOrderReminder(String restaurantId, int days) {
        LocalDate today = clock.today();
        Map<Integer, RestaurantOperatingHour> hours = hoursByDay(restaurantId);
        for (int i = 1; i <= days; i++) {
            LocalDate date = today.plusDays(i);
            RestaurantOperatingHour h = hours.get(date.getDayOfWeek().getValue());
            if (h == null || h.getOpenTime() == null || h.getCloseTime() == null) {
                return true;
            }
        }
        return availabilityRepository.findByRestaurantId(restaurantId).isEmpty();
    }

    // ------------------------------------------------------------------
    // Full validation used at order placement
    // ------------------------------------------------------------------

    /**
     * Validates a pre-order against every business rule and throws
     * IllegalArgumentException with a customer-facing message on the first
     * failure. All date checks use the business timezone.
     */
    public void validatePreOrder(String restaurantId, LocalDate date, LocalTime pickupTime,
                                 List<String> menuItemIds) {
        LocalDate today = clock.today();
        PreOrderSettings s = settings(restaurantId);
        LocalDate firstDay = today.plusDays(1);
        LocalDate lastDay = today.plusDays(s.getAdvanceDays());

        if (date.isBefore(firstDay)) {
            throw new IllegalArgumentException(
                    "Pre-orders must be placed for a future date (from " + firstDay + ").");
        }
        if (date.isAfter(lastDay)) {
            throw new IllegalArgumentException(
                    "Pre-orders can be placed up to " + s.getAdvanceDays()
                            + " day(s) ahead (until " + lastDay + ").");
        }
        if (isCutoffPassed(restaurantId, date)) {
            throw new IllegalArgumentException(
                    "Pre-order cutoff (" + formatTime(s.getCutoffTime()) + " on "
                            + date.minusDays(1) + ") for " + date + " has passed.");
        }
        if (isPreOrderBlockedOn(restaurantId, date)) {
            throw new IllegalArgumentException(
                    "Restaurant is closed on " + date + " — pre-orders are not accepted for this day.");
        }
        if (pickupTime != null && isPickupOutsideOpenHours(restaurantId, date, pickupTime)) {
            RestaurantOperatingHour h = hoursByDay(restaurantId).get(date.getDayOfWeek().getValue());
            throw new IllegalArgumentException(
                    "Pickup time must be between " + formatTime(h.getOpenTime()) + " and "
                            + formatTime(h.getCloseTime()) + " on " + date + ".");
        }

        List<String> unavailable = new ArrayList<>();
        for (String menuItemId : menuItemIds) {
            if (!isDishAvailable(restaurantId, menuItemId, date)) {
                String title = menuItemRepository.findById(menuItemId)
                        .map(MenuItem::getTitle)
                        .orElse(menuItemId);
                Optional<DishSlotOverride> override = overrideRepository
                        .findByMenuItemIdAndTargetDate(menuItemId, date);
                if (override.isPresent() && "CLOSE".equals(override.get().getAction())) {
                    throw new IllegalArgumentException(
                            title + " is not available for pre-order on " + date
                                    + " (slot closed by manager).");
                }
                unavailable.add(title);
            }
        }
        if (!unavailable.isEmpty()) {
            Set<Integer> days = weeklyDaysFor(menuItemIds.get(0));
            String schedule = days.isEmpty()
                    ? "every day"
                    : sortedWeekdays(days).stream()
                        .map(PreOrderAvailabilityService::weekdayLabel)
                        .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Not available for pre-order on " + date + ": " + String.join(", ", unavailable)
                            + ". " + (unavailable.size() == 1 ? "This dish" : "One of these dishes")
                            + " is only available on: " + schedule + ".");
        }
    }

    // ------------------------------------------------------------------
    // Checkout helper: orderable dates for a set of dishes
    // ------------------------------------------------------------------

    /**
     * Returns next {@code daysAhead} dates with per-date and per-dish pre-order
     * availability, for the checkout UI. Restaurant closure and cutoff are
     * evaluated server-side; the UI simply disables dates where orderable=false.
     */
    public List<Map<String, Object>> availableDates(String restaurantId, List<String> menuItemIds,
                                                    Integer daysAhead) {
        PreOrderSettings s = settings(restaurantId);
        int days = daysAhead != null ? daysAhead : s.getAdvanceDays();
        LocalDate today = clock.today();
        Map<Integer, RestaurantOperatingHour> hours = hoursByDay(restaurantId);
        Map<String, String> titles = new HashMap<>();
        for (String id : menuItemIds) {
            menuItemRepository.findById(id).ifPresent(m -> titles.put(id, m.getTitle()));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 1; i <= days; i++) {
            LocalDate date = today.plusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("weekday", date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL,
                    java.util.Locale.ENGLISH));

            RestaurantOperatingHour h = hours.get(date.getDayOfWeek().getValue());
            String open = h != null && h.getOpenTime() != null ? formatTime(h.getOpenTime()) : null;
            String close = h != null && h.getCloseTime() != null ? formatTime(h.getCloseTime()) : null;
            row.put("openTime", open);
            row.put("closeTime", close);

            List<String> reasons = new ArrayList<>();
            boolean closed = isPreOrderBlockedOn(restaurantId, date);
            boolean cutoff = isCutoffPassed(restaurantId, date);
            if (closed) reasons.add("Restaurant closed on " + date);
            if (cutoff) reasons.add("Cutoff passed (" + formatTime(s.getCutoffTime()) + " on " + date.minusDays(1) + ")");

            List<Map<String, Object>> dishes = new ArrayList<>();
            for (String id : menuItemIds) {
                boolean available = !closed && isDishAvailable(restaurantId, id, date);
                Map<String, Object> dish = new LinkedHashMap<>();
                dish.put("menuItemId", id);
                dish.put("title", titles.getOrDefault(id, id));
                dish.put("available", available);
                if (!available) {
                    dish.put("reason", "Not available on " + date);
                }
                dishes.add(dish);
            }
            boolean orderable = !closed && !cutoff
                    && dishes.stream().allMatch(d -> (Boolean) d.get("available"));
            row.put("orderable", orderable);
            row.put("reasons", reasons);
            row.put("dishes", dishes);
            result.add(row);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static String weekdayLabel(Integer dayOfWeek) {
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) return "";
        return DAY_NAMES.get(dayOfWeek - 1);
    }

    public static String formatTime(LocalTime time) {
        return time != null ? time.format(DateTimeFormatter.ofPattern("hh:mm a")) : "—";
    }

    public static List<Integer> sortedWeekdays(Set<Integer> days) {
        return days.stream().sorted().toList();
    }
}
