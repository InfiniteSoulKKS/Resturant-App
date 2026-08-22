package com.savorystay.service;

import com.savorystay.entity.MenuItem;
import com.savorystay.repository.MenuItemIngredientRepository;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.PriceRuleRepository;
import com.savorystay.repository.IngredientRepository;
import com.savorystay.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MenuService SSE broadcast functionality:
 * - Status change broadcasts to all users
 * - update() correctly detects status changes (the old-status-before-mutation fix)
 * - Daily plate count is included in broadcast events
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceSseTest {

    @Mock MenuItemRepository menuItemRepository;
    @Mock MenuItemIngredientRepository ingredientRepository;
    @Mock PriceRuleRepository priceRuleRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock IngredientRepository ingredientMasterRepository;
    @Mock RealtimeService realtimeService;
    @Mock OrderItemRepository orderItemRepository;

    private MenuService menuService;

    private static final String REST_ID = "REST_TEST";
    private static final String ITEM_ID = "MI_TEST";

    @BeforeEach
    void setUp() {
        menuService = new MenuService(
                menuItemRepository, ingredientRepository, priceRuleRepository,
                restaurantRepository, ingredientMasterRepository, realtimeService,
                orderItemRepository);
    }

    private MenuItem menuItem(String status) {
        return MenuItem.builder()
                .id(ITEM_ID).restaurantId(REST_ID).title("Butter Chicken")
                .price(new BigDecimal("420")).category("Mains").status(status)
                .isVeg(false).spiceLevel("Medium").dailyPlateCount(30)
                .build();
    }

    @Test
    void updateStatus_broadcastsAvailabilityEvent() {
        MenuItem item = menuItem("Available");
        when(menuItemRepository.findByIdAndRestaurantId(ITEM_ID, REST_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        menuService.updateStatus(ITEM_ID, REST_ID, "Sold Out");

        // Should broadcast to all users
        verify(realtimeService).broadcastToAllUsers(eq("menu_availability"), any(Map.class));
        // Should also broadcast to restaurant staff
        verify(realtimeService).pushToRestaurant(eq(REST_ID), eq("menu_availability"), any(Map.class));
    }

    @Test
    void updateStatus_broadcastIncludesRemainingPlates() {
        MenuItem item = menuItem("Available");
        when(menuItemRepository.findByIdAndRestaurantId(ITEM_ID, REST_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.countPlatesOrderedForItem(eq(ITEM_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(20L);

        menuService.updateStatus(ITEM_ID, REST_ID, "Sold Out");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(realtimeService).broadcastToAllUsers(eq("menu_availability"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertEquals(ITEM_ID, event.get("menuItemId"));
        assertEquals("Sold Out", event.get("status"));
        assertEquals(30, event.get("dailyPlateCount"));
        assertEquals(10, event.get("remainingPlates")); // 30 - 20 = 10
        assertEquals(REST_ID, event.get("restaurantId"));
        assertNotNull(event.get("timestamp"));
    }

    @Test
    void updateStatus_broadcastWithNullDailyPlateCount() {
        MenuItem item = menuItem("Available");
        item.setDailyPlateCount(null);
        when(menuItemRepository.findByIdAndRestaurantId(ITEM_ID, REST_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        menuService.updateStatus(ITEM_ID, REST_ID, "Sold Out");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(realtimeService).broadcastToAllUsers(eq("menu_availability"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertNull(event.get("dailyPlateCount"));
        assertNull(event.get("remainingPlates")); // no cap = null
    }

    @Test
    void update_detectsStatusChangeAndBroadcasts() {
        // This tests the fix: old status is captured BEFORE mutation
        MenuItem item = menuItem("Available");
        when(menuItemRepository.findByIdAndRestaurantId(ITEM_ID, REST_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.countPlatesOrderedForItem(eq(ITEM_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0L);

        MenuItem updates = MenuItem.builder().status("Sold Out").build();
        menuService.update(ITEM_ID, REST_ID, updates, null);

        // Should broadcast because status changed from Available → Sold Out
        verify(realtimeService).broadcastToAllUsers(eq("menu_availability"), any(Map.class));
    }

    @Test
    void update_doesNotBroadcastWhenStatusUnchanged() {
        MenuItem item = menuItem("Available");
        when(menuItemRepository.findByIdAndRestaurantId(ITEM_ID, REST_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // Update without changing status
        MenuItem updates = MenuItem.builder().title("New Title").build();
        menuService.update(ITEM_ID, REST_ID, updates, null);

        // Should NOT broadcast — status didn't change
        verify(realtimeService, never()).broadcastToAllUsers(anyString(), any());
    }

    @Test
    void update_broadcastsWhenRestoringFromSoldOut() {
        MenuItem item = menuItem("Sold Out");
        when(menuItemRepository.findByIdAndRestaurantId(ITEM_ID, REST_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.countPlatesOrderedForItem(eq(ITEM_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(5L);

        MenuItem updates = MenuItem.builder().status("Available").build();
        menuService.update(ITEM_ID, REST_ID, updates, null);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(realtimeService).broadcastToAllUsers(eq("menu_availability"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertEquals("Available", event.get("status"));
        assertEquals(25, event.get("remainingPlates")); // 30 - 5 = 25
    }

    @Test
    void broadcastPlatesAtZero_marksAsSoldOut() {
        MenuItem item = menuItem("Available");
        when(menuItemRepository.findByIdAndRestaurantId(ITEM_ID, REST_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));
        // All 30 plates ordered — remaining = 0
        when(orderItemRepository.countPlatesOrderedForItem(eq(ITEM_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(30L);

        menuService.updateStatus(ITEM_ID, REST_ID, "Available");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(realtimeService).broadcastToAllUsers(eq("menu_availability"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertEquals(0, event.get("remainingPlates"));
    }
}
