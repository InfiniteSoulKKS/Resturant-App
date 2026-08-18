package com.savorystay.service;

import com.savorystay.common.BusinessClock;
import com.savorystay.entity.DishAvailability;
import com.savorystay.entity.DishSlotOverride;
import com.savorystay.entity.PreOrderSettings;
import com.savorystay.entity.RestaurantOperatingHour;
import com.savorystay.repository.DishAvailabilityRepository;
import com.savorystay.repository.DishSlotOverrideRepository;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.PreOrderSettingsRepository;
import com.savorystay.repository.RestaurantOperatingHourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the pre-order business rules: cutoff time (exactly-at =
 * closed), closure (full holiday, 2nd-half-close at 14:00), dish availability
 * (weekly schedule, explicit OPEN/CLOSE overrides, precedence CLOSE > OPEN >
 * schedule, restaurant closure always wins) and the ordering horizon.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreOrderAvailabilityServiceTest {

    private static final String RESTAURANT = "REST_TEST";
    private static final String DISH = "MI_1";

    @Mock RestaurantOperatingHourRepository operatingHourRepository;
    @Mock PreOrderSettingsRepository settingsRepository;
    @Mock DishAvailabilityRepository availabilityRepository;
    @Mock DishSlotOverrideRepository overrideRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock BusinessClock clock;

    private PreOrderAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new PreOrderAvailabilityService(
                operatingHourRepository, settingsRepository, availabilityRepository,
                overrideRepository, menuItemRepository, clock);
        // Default: business "today" is a fixed Monday 2026-08-10 08:00.
        // Lenient — individual tests override most of these.
        org.mockito.Mockito.lenient().when(clock.today()).thenReturn(LocalDate.of(2026, 8, 10)); // Monday
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(LocalDateTime.of(2026, 8, 10, 8, 0));
        org.mockito.Mockito.lenient().when(settingsRepository.findByRestaurantId(RESTAURANT)).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(availabilityRepository.findByMenuItemId(DISH)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(overrideRepository.findByMenuItemIdAndTargetDate(eq(DISH), any())).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------
    // Operating hours / closure
    // ------------------------------------------------------------------

    private RestaurantOperatingHour hours(int dayOfWeek, LocalTime open, LocalTime close, boolean closed) {
        return RestaurantOperatingHour.builder()
                .restaurantId(RESTAURANT).dayOfWeek(dayOfWeek)
                .openTime(open).closeTime(close).closed(closed).build();
    }

    @Test
    void fullHolidayBlocksAllPreOrders() {
        // Tuesday 2026-08-11 is closed all day
        when(operatingHourRepository.findByRestaurantId(RESTAURANT))
                .thenReturn(List.of(hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), true)));
        assertTrue(service.isPreOrderBlockedOn(RESTAURANT, LocalDate.of(2026, 8, 11)));
    }

    @Test
    void secondHalfClosedDayBlocksPreOrders() {
        // Monday closes at 14:00 ("2nd half closed") → blocks pre-orders for Monday
        when(operatingHourRepository.findByRestaurantId(RESTAURANT))
                .thenReturn(List.of(hours(1, LocalTime.of(9, 0), LocalTime.of(14, 0), false)));
        assertTrue(service.isPreOrderBlockedOn(RESTAURANT, LocalDate.of(2026, 8, 17))); // next Monday
    }

    @Test
    void fullyOpenDayAllowsPreOrders() {
        when(operatingHourRepository.findByRestaurantId(RESTAURANT))
                .thenReturn(List.of(hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        assertFalse(service.isPreOrderBlockedOn(RESTAURANT, LocalDate.of(2026, 8, 11)));
    }

    @Test
    void unconfiguredDayIsTreatedAsOpen() {
        // no hours row → not blocked (backward compatible, manager is reminded to configure)
        assertFalse(service.isPreOrderBlockedOn(RESTAURANT, LocalDate.of(2026, 8, 11)));
    }

    @Test
    void pickupOutsideOpenHoursRejected() {
        when(operatingHourRepository.findByRestaurantId(RESTAURANT))
                .thenReturn(List.of(hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        LocalDate tue = LocalDate.of(2026, 8, 11);
        assertTrue(service.isPickupOutsideOpenHours(RESTAURANT, tue, LocalTime.of(8, 0)));
        assertTrue(service.isPickupOutsideOpenHours(RESTAURANT, tue, LocalTime.of(23, 30)));
        assertFalse(service.isPickupOutsideOpenHours(RESTAURANT, tue, LocalTime.of(19, 30)));
    }

    // ------------------------------------------------------------------
    // Cutoff
    // ------------------------------------------------------------------

    @Test
    void cutoffBefore9AmAllowsOrderForTomorrow() {
        // now = Mon 08:00 → Tuesday pre-orders still open
        assertFalse(service.isCutoffPassed(RESTAURANT, LocalDate.of(2026, 8, 11)));
        service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 11), LocalTime.of(19, 0), List.of(DISH));
    }

    @Test
    void cutoffExactlyAt9AmIsClosed() {
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 8, 10, 9, 0, 0));
        assertTrue(service.isCutoffPassed(RESTAURANT, LocalDate.of(2026, 8, 11)));
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 11), LocalTime.of(19, 0), List.of(DISH)));
    }

    @Test
    void cutoffAfter9AmIsClosed() {
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 8, 10, 9, 1));
        assertTrue(service.isCutoffPassed(RESTAURANT, LocalDate.of(2026, 8, 11)));
    }

    @Test
    void cutoffTwoDaysOutStillOpen() {
        // now = Mon 20:00 → Wednesday (D-1 = Tuesday) cutoff is Tue 09:00 → open
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 8, 10, 20, 0));
        assertFalse(service.isCutoffPassed(RESTAURANT, LocalDate.of(2026, 8, 12)));
    }

    @Test
    void customCutoffTimeIsHonored() {
        when(settingsRepository.findByRestaurantId(RESTAURANT)).thenReturn(Optional.of(
                PreOrderSettings.builder().restaurantId(RESTAURANT).cutoffTime(LocalTime.of(12, 0)).advanceDays(7).build()));
        // now = Mon 11:00 → Tuesday still open; Mon 13:00 → closed
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 8, 10, 11, 0));
        assertFalse(service.isCutoffPassed(RESTAURANT, LocalDate.of(2026, 8, 11)));
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 8, 10, 13, 0));
        assertTrue(service.isCutoffPassed(RESTAURANT, LocalDate.of(2026, 8, 11)));
    }

    // ------------------------------------------------------------------
    // Horizon
    // ------------------------------------------------------------------

    @Test
    void rejectsTodayAndPastDates() {
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 10), null, List.of(DISH)));
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 9), null, List.of(DISH)));
    }

    @Test
    void rejectsBeyondAdvanceHorizon() {
        // default advanceDays = 7 → last day is 2026-08-17
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 18), null, List.of(DISH)));
    }

    // ------------------------------------------------------------------
    // Dish availability: weekly schedule
    // ------------------------------------------------------------------

    @Test
    void dishAvailableOnScheduledWeekday() {
        // Dish scheduled for Wednesday (3) only
        when(availabilityRepository.findByMenuItemId(DISH)).thenReturn(List.of(
                DishAvailability.builder().menuItemId(DISH).dayOfWeek(3).build()));
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(3, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));

        assertTrue(service.isDishAvailable(RESTAURANT, DISH, LocalDate.of(2026, 8, 12))); // Wed
        assertFalse(service.isDishAvailable(RESTAURANT, DISH, LocalDate.of(2026, 8, 11))); // Tue

        // Full validation succeeds on the scheduled day, fails on the off day
        service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 12), LocalTime.of(19, 0), List.of(DISH));
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 11), LocalTime.of(19, 0), List.of(DISH)));
    }

    @Test
    void dishWithoutScheduleAvailableEveryDay() {
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(3, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        assertTrue(service.isDishAvailable(RESTAURANT, DISH, LocalDate.of(2026, 8, 12)));
        assertTrue(service.isDishAvailable(RESTAURANT, DISH, LocalDate.of(2026, 8, 11)));
    }

    // ------------------------------------------------------------------
    // Overrides & precedence: CLOSE > OPEN > schedule
    // ------------------------------------------------------------------

    @Test
    void explicitOpenWinsOverWeeklySchedule() {
        // Dish normally available Wednesdays; manager opens an extra Tuesday
        when(availabilityRepository.findByMenuItemId(DISH)).thenReturn(List.of(
                DishAvailability.builder().menuItemId(DISH).dayOfWeek(3).build()));
        when(overrideRepository.findByMenuItemIdAndTargetDate(eq(DISH), any())).thenReturn(Optional.of(
                DishSlotOverride.builder().menuItemId(DISH).action("OPEN")
                        .targetDate(LocalDate.of(2026, 8, 11)).build()));
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));

        assertTrue(service.isDishAvailable(RESTAURANT, DISH, LocalDate.of(2026, 8, 11)));
        service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 11), LocalTime.of(19, 0), List.of(DISH));
    }

    @Test
    void explicitCloseWinsOverSchedule() {
        // Dish normally available Wednesdays; manager closes this Wednesday
        when(availabilityRepository.findByMenuItemId(DISH)).thenReturn(List.of(
                DishAvailability.builder().menuItemId(DISH).dayOfWeek(3).build()));
        when(overrideRepository.findByMenuItemIdAndTargetDate(eq(DISH), any())).thenReturn(Optional.of(
                DishSlotOverride.builder().menuItemId(DISH).action("CLOSE")
                        .targetDate(LocalDate.of(2026, 8, 12)).build()));
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(3, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));

        assertFalse(service.isDishAvailable(RESTAURANT, DISH, LocalDate.of(2026, 8, 12)));
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 12), LocalTime.of(19, 0), List.of(DISH)));
    }

    @Test
    void closureWinsEvenOverExplicitOpen() {
        // Manager opens Tuesday but Tuesday is a full holiday → still blocked
        when(overrideRepository.findByMenuItemIdAndTargetDate(eq(DISH), any())).thenReturn(Optional.of(
                DishSlotOverride.builder().menuItemId(DISH).action("OPEN")
                        .targetDate(LocalDate.of(2026, 8, 11)).build()));
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), true))); // Tue holiday

        assertTrue(service.isPreOrderBlockedOn(RESTAURANT, LocalDate.of(2026, 8, 11)));
        assertThrows(IllegalArgumentException.class, () ->
                service.validatePreOrder(RESTAURANT, LocalDate.of(2026, 8, 11), LocalTime.of(19, 0), List.of(DISH)));
    }

    // ------------------------------------------------------------------
    // availableDates (checkout UI)
    // ------------------------------------------------------------------

    @Test
    void availableDatesMarksClosedAndCutoffDaysUnorderable() {
        // All days open 09:00-23:00; Tuesday (2026-08-11) closed all day
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), true),
                hours(3, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                hours(4, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                hours(5, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                hours(6, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                hours(7, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        when(menuItemRepository.findById(DISH)).thenReturn(Optional.empty());
        when(availabilityRepository.findByMenuItemId(DISH)).thenReturn(List.of());
        when(overrideRepository.findByMenuItemIdAndTargetDate(eq(DISH), any())).thenReturn(Optional.empty());

        List<Map<String, Object>> dates = service.availableDates(RESTAURANT, List.of(DISH), 7);
        assertEquals(7, dates.size());
        // index 0 = 2026-08-11 (Tuesday, holiday) → not orderable
        Map<String, Object> tue = dates.get(0);
        assertEquals("2026-08-11", tue.get("date"));
        assertEquals(false, tue.get("orderable"));
        assertTrue(((List<?>) tue.get("reasons")).stream().anyMatch(r -> r.toString().contains("closed")));
        // index 1 = 2026-08-12 (Wednesday) fully open → orderable
        Map<String, Object> wed = dates.get(1);
        assertEquals("2026-08-12", wed.get("date"));
        assertEquals(true, wed.get("orderable"));
    }

    @Test
    void reminderFiresWhenHoursMissingAndNotWhenConfigured() {
        // No hours configured → reminder needed
        when(availabilityRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                DishAvailability.builder().menuItemId(DISH).dayOfWeek(3).build()));
        assertTrue(service.needsPreOrderReminder(RESTAURANT, 3));

        // Full week configured + dish availability → no reminder
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(
                List.of(hours(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(3, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(4, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(5, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(6, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(7, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        assertFalse(service.needsPreOrderReminder(RESTAURANT, 3));
    }

    @Test
    void deliberateClosureDoesNotTriggerReminder() {
        // Every day is deliberately configured (incl. a full holiday Sunday) → no reminder
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(
                List.of(hours(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(3, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(4, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(5, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(6, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(7, null, null, true)));
        when(availabilityRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                DishAvailability.builder().menuItemId(DISH).dayOfWeek(3).build()));
        assertFalse(service.needsPreOrderReminder(RESTAURANT, 3));
    }
}
