package com.savorystay.service;

import com.savorystay.dto.OperatingHourRequest;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the per-restaurant pre-order cutoff rule: the cutoff must
 * never be after the restaurant's opening time on any open day (the cutoff for
 * date D falls on D-1, which can be any weekday). Enforced both when saving
 * the cutoff ({@code updateSettings}) and when changing operating hours
 * ({@code upsertOperatingHour}) so the configuration can never become invalid.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreOrderConfigServiceTest {

    private static final String RESTAURANT = "REST_TEST";

    @Mock RestaurantOperatingHourRepository operatingHourRepository;
    @Mock PreOrderSettingsRepository settingsRepository;
    @Mock DishAvailabilityRepository availabilityRepository;
    @Mock DishSlotOverrideRepository overrideRepository;
    @Mock MenuItemRepository menuItemRepository;

    private PreOrderConfigService service;

    @BeforeEach
    void setUp() {
        service = new PreOrderConfigService(
                operatingHourRepository, settingsRepository, availabilityRepository,
                overrideRepository, menuItemRepository);
        // Default: every day open 09:00-23:00, no settings row yet.
        org.mockito.Mockito.lenient().when(operatingHourRepository.findByRestaurantId(RESTAURANT))
                .thenReturn(List.of(
                        hours(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(2, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(3, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(4, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(5, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(6, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                        hours(7, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        org.mockito.Mockito.lenient().when(settingsRepository.findByRestaurantId(RESTAURANT))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(settingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private RestaurantOperatingHour hours(int day, LocalTime open, LocalTime close, boolean closed) {
        return RestaurantOperatingHour.builder()
                .restaurantId(RESTAURANT).dayOfWeek(day)
                .openTime(open).closeTime(close).closed(closed).build();
    }

    // ------------------------------------------------------------------
    // updateSettings — cutoff must not be after opening time
    // ------------------------------------------------------------------

    @Test
    void cutoffAtOpeningTimeIsAccepted() {
        // cutoff 09:00 == open 09:00 → allowed
        PreOrderSettings saved = service.updateSettings(RESTAURANT, LocalTime.of(9, 0), 7);
        assertEquals(LocalTime.of(9, 0), saved.getCutoffTime());
        assertEquals(7, saved.getAdvanceDays());
        verify(settingsRepository).save(any());
    }

    @Test
    void cutoffBeforeOpeningTimeIsAccepted() {
        // cutoff 08:30 < open 09:00 → allowed
        PreOrderSettings saved = service.updateSettings(RESTAURANT, LocalTime.of(8, 30), 7);
        assertEquals(LocalTime.of(8, 30), saved.getCutoffTime());
    }

    @Test
    void cutoffAfterOpeningTimeIsRejected() {
        // cutoff 10:00 > open 09:00 → rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(RESTAURANT, LocalTime.of(10, 0), 7));
        assertTrue(ex.getMessage().contains("cannot be after the restaurant opening time"));
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void cutoffValidatedAgainstEveryOpenDay() {
        // Sunday (7) opens at 08:00 → a 08:30 cutoff is too late on Sundays
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false),
                hours(7, LocalTime.of(8, 0), LocalTime.of(23, 0), false)));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(RESTAURANT, LocalTime.of(8, 30), 7));
    }

    @Test
    void closedDaysImposeNoCutoffConstraint() {
        // Only closed days configured → nothing opens, so any cutoff is valid
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of(
                hours(7, null, null, true)));
        PreOrderSettings saved = service.updateSettings(RESTAURANT, LocalTime.of(11, 0), 7);
        assertEquals(LocalTime.of(11, 0), saved.getCutoffTime());
    }

    @Test
    void cutoffAllowedWhenNoHoursConfigured() {
        when(operatingHourRepository.findByRestaurantId(RESTAURANT)).thenReturn(List.of());
        PreOrderSettings saved = service.updateSettings(RESTAURANT, LocalTime.of(12, 0), 7);
        assertEquals(LocalTime.of(12, 0), saved.getCutoffTime());
    }

    @Test
    void advanceDaysOnlyUpdateKeepsExistingCutoff() {
        when(settingsRepository.findByRestaurantId(RESTAURANT)).thenReturn(Optional.of(
                PreOrderSettings.builder().restaurantId(RESTAURANT).cutoffTime(LocalTime.of(9, 0)).advanceDays(7).build()));
        PreOrderSettings saved = service.updateSettings(RESTAURANT, null, 3);
        assertEquals(LocalTime.of(9, 0), saved.getCutoffTime());
        assertEquals(3, saved.getAdvanceDays());
    }

    // ------------------------------------------------------------------
    // upsertOperatingHour — hours change must not invalidate the cutoff
    // ------------------------------------------------------------------

    @Test
    void hoursChangeBeforeExistingCutoffRejected() {
        // Restaurant has a stored cutoff of 09:00; manager moves opening to 08:00
        when(settingsRepository.findByRestaurantId(RESTAURANT)).thenReturn(Optional.of(
                PreOrderSettings.builder().restaurantId(RESTAURANT).cutoffTime(LocalTime.of(9, 0)).advanceDays(7).build()));
        when(operatingHourRepository.findByRestaurantIdAndDayOfWeek(eq(RESTAURANT), eq(1)))
                .thenReturn(Optional.of(hours(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertOperatingHour(RESTAURANT,
                        new OperatingHourRequest(1, LocalTime.of(8, 0), LocalTime.of(23, 0), false)));
        assertTrue(ex.getMessage().contains("after this day's opening time"));
        verify(operatingHourRepository, never()).save(any());
    }

    @Test
    void hoursChangeAtOrAfterExistingCutoffAccepted() {
        when(settingsRepository.findByRestaurantId(RESTAURANT)).thenReturn(Optional.of(
                PreOrderSettings.builder().restaurantId(RESTAURANT).cutoffTime(LocalTime.of(9, 0)).advanceDays(7).build()));
        when(operatingHourRepository.findByRestaurantIdAndDayOfWeek(eq(RESTAURANT), eq(1)))
                .thenReturn(Optional.of(hours(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        when(operatingHourRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RestaurantOperatingHour saved = service.upsertOperatingHour(RESTAURANT,
                new OperatingHourRequest(1, LocalTime.of(9, 0), LocalTime.of(23, 0), false));
        assertEquals(LocalTime.of(9, 0), saved.getOpenTime());
    }

    @Test
    void closingADayWithLateCutoffIsAccepted() {
        // Manager closes Sunday entirely → no constraint; an 11:00 cutoff is fine
        when(settingsRepository.findByRestaurantId(RESTAURANT)).thenReturn(Optional.of(
                PreOrderSettings.builder().restaurantId(RESTAURANT).cutoffTime(LocalTime.of(11, 0)).advanceDays(7).build()));
        when(operatingHourRepository.findByRestaurantIdAndDayOfWeek(eq(RESTAURANT), eq(7)))
                .thenReturn(Optional.of(hours(7, LocalTime.of(9, 0), LocalTime.of(23, 0), false)));
        when(operatingHourRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RestaurantOperatingHour saved = service.upsertOperatingHour(RESTAURANT,
                new OperatingHourRequest(7, null, null, true));
        assertEquals(true, saved.getClosed());
    }

    @Test
    void settingHoursOnNewDayUsesDefaultsAndPasses() {
        // No existing row for Tuesday; cutoff default 09:00 == default open 09:00
        when(operatingHourRepository.findByRestaurantIdAndDayOfWeek(eq(RESTAURANT), eq(2)))
                .thenReturn(Optional.empty());
        when(operatingHourRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RestaurantOperatingHour saved = service.upsertOperatingHour(RESTAURANT,
                new OperatingHourRequest(2, LocalTime.of(10, 0), LocalTime.of(22, 0), false));
        assertEquals(LocalTime.of(10, 0), saved.getOpenTime());
        assertEquals(LocalTime.of(22, 0), saved.getCloseTime());
    }
}
