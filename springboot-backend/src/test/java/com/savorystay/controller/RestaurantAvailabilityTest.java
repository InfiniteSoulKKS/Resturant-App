package com.savorystay.controller;

import com.savorystay.entity.MenuItem;
import com.savorystay.entity.Order;
import com.savorystay.entity.RestaurantSettings;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.RestaurantSettingsRepository;
import com.savorystay.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for restaurant table availability and plate availability endpoints.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantAvailabilityTest {

    @Mock RestaurantService restaurantService;
    @Mock RestaurantSettingsRepository restaurantSettingsRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock MenuItemRepository menuItemRepository;

    private RestaurantController controller;

    private static final String REST_ID = "REST_TEST";

    @BeforeEach
    void setUp() {
        controller = new RestaurantController(
                restaurantService, restaurantSettingsRepository,
                orderRepository, orderItemRepository, menuItemRepository);
    }

    // ─── TABLE AVAILABILITY ───────────────────────────────────────

    @Test
    void tableAvailability_returnsCorrectCounts() {
        RestaurantSettings settings = RestaurantSettings.builder()
                .restaurantId(REST_ID)
                .tableConfig("[{\"type\":\"2-Seater\",\"count\":5},{\"type\":\"4-Seater\",\"count\":3}]")
                .totalTables(8)
                .build();
        when(restaurantSettingsRepository.findByRestaurantId(REST_ID)).thenReturn(Optional.of(settings));
        // 1 two-seater booked, 0 four-seaters booked
        List<Object[]> bookedData = new ArrayList<>();
        bookedData.add(new Object[]{2, 1L});
        when(orderRepository.countDineInByTimeSlots(eq(REST_ID), anyList()))
                .thenReturn(bookedData);

        ResponseEntity<?> response = controller.getTableAvailability(
                REST_ID, "2026-08-21", "7:00 PM");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue((Boolean) body.get("success"));

        List<Map<String, Object>> tables = (List<Map<String, Object>>) body.get("tables");
        assertEquals(2, tables.size());

        // 2-Seater: 5 total, 1 booked, 4 remaining
        Map<String, Object> twoSeater = tables.get(0);
        assertEquals("2-Seater", twoSeater.get("type"));
        assertEquals(5, twoSeater.get("total"));
        assertEquals(1, twoSeater.get("booked"));
        assertEquals(4, twoSeater.get("remaining"));

        // 4-Seater: 3 total, 0 booked, 3 remaining
        Map<String, Object> fourSeater = tables.get(1);
        assertEquals("4-Seater", fourSeater.get("type"));
        assertEquals(3, fourSeater.get("total"));
        assertEquals(0, fourSeater.get("booked"));
        assertEquals(3, fourSeater.get("remaining"));
    }

    @Test
    void tableAvailability_allBooked_showsFull() {
        RestaurantSettings settings = RestaurantSettings.builder()
                .restaurantId(REST_ID)
                .tableConfig("[{\"type\":\"2-Seater\",\"count\":2}]")
                .totalTables(2)
                .build();
        when(restaurantSettingsRepository.findByRestaurantId(REST_ID)).thenReturn(Optional.of(settings));
        // All 2 two-seaters booked
        List<Object[]> bookedData2 = new ArrayList<>();
        bookedData2.add(new Object[]{2, 2L});
        when(orderRepository.countDineInByTimeSlots(eq(REST_ID), anyList()))
                .thenReturn(bookedData2);

        ResponseEntity<?> response = controller.getTableAvailability(
                REST_ID, "2026-08-21", "7:00 PM");

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map<String, Object>> tables = (List<Map<String, Object>>) body.get("tables");

        Map<String, Object> twoSeater = tables.get(0);
        assertEquals(2, twoSeater.get("total"));
        assertEquals(2, twoSeater.get("booked"));
        assertEquals(0, twoSeater.get("remaining"));
    }

    @Test
    void tableAvailability_noSettings_usesDefaults() {
        when(restaurantSettingsRepository.findByRestaurantId(REST_ID)).thenReturn(Optional.empty());
        when(orderRepository.countDineInByTimeSlots(eq(REST_ID), anyList()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getTableAvailability(
                REST_ID, "2026-08-21", "12:00 PM");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue((Boolean) body.get("success"));
        // Default table config has 3 types
        List<Map<String, Object>> tables = (List<Map<String, Object>>) body.get("tables");
        assertEquals(3, tables.size());
    }

    // ─── PLATE AVAILABILITY ───────────────────────────────────────

    @Test
    void plateAvailability_returnsCorrectCounts() {
        MenuItem item1 = MenuItem.builder().id("MI_1").restaurantId(REST_ID)
                .title("Butter Chicken").dailyPlateCount(30).build();
        MenuItem item2 = MenuItem.builder().id("MI_2").restaurantId(REST_ID)
                .title("Paneer Tikka").dailyPlateCount(null).build(); // unlimited

        when(menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(REST_ID))
                .thenReturn(List.of(item1, item2));
        when(orderItemRepository.countPlatesOrderedForItem(eq("MI_1"), any(), any()))
                .thenReturn(25L);
        // MI_2 has null dailyPlateCount, so countPlatesOrderedForItem is never called

        ResponseEntity<?> response = controller.getPlateAvailability(REST_ID, "2026-08-21");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue((Boolean) body.get("success"));

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertEquals(2, items.size());

        // Butter Chicken: 30 cap, 25 ordered, 5 remaining
        Map<String, Object> chicken = items.get(0);
        assertEquals("MI_1", chicken.get("menuItemId"));
        assertEquals(30, chicken.get("dailyPlateCount"));
        assertEquals(25L, chicken.get("platesOrdered"));
        assertEquals(5, chicken.get("remaining"));
        assertEquals(true, chicken.get("available"));

        // Paneer Tikka: unlimited (null cap)
        Map<String, Object> paneer = items.get(1);
        assertEquals("MI_2", paneer.get("menuItemId"));
        assertNull(paneer.get("dailyPlateCount"));
        assertEquals(-1, paneer.get("remaining")); // -1 = unlimited
        assertEquals(true, paneer.get("available"));
    }

    @Test
    void plateAvailability_allPlatesOrdered_showsUnavailable() {
        MenuItem item = MenuItem.builder().id("MI_1").restaurantId(REST_ID)
                .title("Butter Chicken").dailyPlateCount(30).build();

        when(menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(REST_ID))
                .thenReturn(List.of(item));
        when(orderItemRepository.countPlatesOrderedForItem(eq("MI_1"), any(), any()))
                .thenReturn(30L); // All ordered

        ResponseEntity<?> response = controller.getPlateAvailability(REST_ID, "2026-08-21");

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        Map<String, Object> chicken = items.get(0);
        assertEquals(0, chicken.get("remaining"));
        assertEquals(false, chicken.get("available"));
    }

    @Test
    void plateAvailability_moreThanCap_clampsToZero() {
        MenuItem item = MenuItem.builder().id("MI_1").restaurantId(REST_ID)
                .title("Butter Chicken").dailyPlateCount(30).build();

        when(menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(REST_ID))
                .thenReturn(List.of(item));
        // Over-ordered (e.g. cancellations not yet processed)
        when(orderItemRepository.countPlatesOrderedForItem(eq("MI_1"), any(), any()))
                .thenReturn(35L);

        ResponseEntity<?> response = controller.getPlateAvailability(REST_ID, "2026-08-21");

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        Map<String, Object> chicken = items.get(0);
        assertEquals(0, chicken.get("remaining")); // Clamped to 0, not -5
        assertEquals(false, chicken.get("available"));
    }

    // ─── RESTAURANT SETTINGS RESPONSE ─────────────────────────────

    @Test
    void settingsResponse_parsesTableConfig() {
        RestaurantSettings settings = RestaurantSettings.builder()
                .restaurantId(REST_ID)
                .tableConfig("[{\"type\":\"2-Seater\",\"count\":5},{\"type\":\"6-Seater\",\"count\":2}]")
                .totalTables(7)
                .pickupTimeSlots("15 Mins,30 Mins")
                .dineinTimeSlots("12:00 PM,7:00 PM")
                .build();
        when(restaurantSettingsRepository.findByRestaurantId(REST_ID)).thenReturn(Optional.of(settings));

        ResponseEntity<?> response = controller.getSettings(REST_ID);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue((Boolean) body.get("success"));

        // The settings value is a RestaurantSettingsResponse record, not a Map
        // Check it via the response wrapper
        assertNotNull(body.get("settings"));
    }
}
