package com.savorystay.service;

import com.savorystay.entity.Ingredient;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;
import com.savorystay.repository.IngredientRepository;
import com.savorystay.repository.InventoryLedgerRepository;
import com.savorystay.repository.MenuItemIngredientRepository;
import com.savorystay.repository.OrderItemRepository;
import com.savorystay.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the ingredient estimation used by the pre-order feature:
 * recipe-per-plate × ordered plates, aggregation across dishes that share an
 * ingredient, multiple pre-orders summing up, and the per-dish breakdown.
 */
@ExtendWith(MockitoExtension.class)
class IngredientForecastServiceTest {

    private static final String RESTAURANT = "REST_TEST";
    private static final String BIRYANI = "MI_BIRYANI";
    private static final String PULAO = "MI_PULAO";

    @Mock IngredientRepository ingredientRepository;
    @Mock MenuItemIngredientRepository menuItemIngredientRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock InventoryLedgerRepository inventoryLedgerRepository;
    @Mock OutboxService outboxService;

    private IngredientService service;

    @BeforeEach
    void setUp() {
        service = new IngredientService(
                ingredientRepository, menuItemIngredientRepository, orderRepository,
                orderItemRepository, inventoryLedgerRepository, outboxService);
    }

    private Order preOrder(String id, String date) {
        return Order.builder().id(id).restaurantId(RESTAURANT).orderType("PRE_ORDER")
                .pickupTime(date + "T19:30:00").orderStatus("NEW").build();
    }

    private OrderItem item(String dishId, String title, int qty) {
        return OrderItem.builder().menuItemId(dishId).title(title).quantity(qty).build();
    }

    private MenuItemIngredient recipe(String dishId, String name, String unit, String perPlate) {
        return MenuItemIngredient.builder().menuItemId(dishId).name(name)
                .unit(unit).quantityPerUnit(new BigDecimal(perPlate)).build();
    }

    @Test
    void singleDishSingleOrderScalesByPlates() {
        // 10 plates of Biryani: Rice 500g/plate → 5000g, Chicken 250g/plate → 2500g
        when(orderRepository.findActiveOrdersBetween(anyString(), any(), any(), any()))
                .thenReturn(List.of(preOrder("o1", "2026-08-11")));
        when(orderItemRepository.findByOrderIdIn(anyList())).thenReturn(List.of(
                item(BIRYANI, "Biryani", 10)));
        when(menuItemIngredientRepository.findByMenuItemIdIn(anyList())).thenReturn(List.of(
                recipe(BIRYANI, "Rice", "g", "500"),
                recipe(BIRYANI, "Chicken", "g", "250")));
        when(ingredientRepository.findByRestaurantIdOrderByNameAsc(RESTAURANT)).thenReturn(List.of());

        Map<String, Object> forecast = service.forecastForDate(RESTAURANT, LocalDate.of(2026, 8, 11));
        List<Map<String, Object>> ingredients = cast(forecast.get("ingredients"));

        Map<String, Object> rice = find(ingredients, "Rice");
        assertEquals(0, new BigDecimal("5000").compareTo((BigDecimal) rice.get("requiredQuantity")));
        Map<String, Object> chicken = find(ingredients, "Chicken");
        assertEquals(0, new BigDecimal("2500").compareTo((BigDecimal) chicken.get("requiredQuantity")));

        // per-dish breakdown present with 10 plates
        List<Map<String, Object>> dishes = cast(forecast.get("dishes"));
        assertEquals(1, dishes.size());
        assertEquals(10, dishes.get(0).get("plates"));
    }

    @Test
    void multipleDishesShareAnIngredientAndAreAggregated() {
        // Biryani 10 plates (500g rice) + Pulao 5 plates (300g rice) = 6500g rice total
        when(orderRepository.findActiveOrdersBetween(anyString(), any(), any(), any()))
                .thenReturn(List.of(preOrder("o1", "2026-08-11"), preOrder("o2", "2026-08-11")));
        when(orderItemRepository.findByOrderIdIn(anyList())).thenReturn(List.of(
                item(BIRYANI, "Biryani", 10),
                item(PULAO, "Pulao", 5)));
        when(menuItemIngredientRepository.findByMenuItemIdIn(anyList())).thenReturn(List.of(
                recipe(BIRYANI, "Rice", "g", "500"),
                recipe(BIRYANI, "Chicken", "g", "250"),
                recipe(PULAO, "Rice", "g", "300")));
        when(ingredientRepository.findByRestaurantIdOrderByNameAsc(RESTAURANT)).thenReturn(List.of());

        Map<String, Object> forecast = service.forecastForDate(RESTAURANT, LocalDate.of(2026, 8, 11));
        List<Map<String, Object>> ingredients = cast(forecast.get("ingredients"));

        // exactly one Rice row, with the sum
        assertEquals(1, ingredients.stream().filter(r -> "Rice".equals(r.get("name"))).count());
        Map<String, Object> rice = find(ingredients, "Rice");
        assertEquals(0, new BigDecimal("6500").compareTo((BigDecimal) rice.get("requiredQuantity")));

        // two dishes in the breakdown
        List<Map<String, Object>> dishes = cast(forecast.get("dishes"));
        assertEquals(2, dishes.size());
    }

    @Test
    void multiplePreOrdersForSameDishSumTogether() {
        // Two separate orders, each 2 plates of Biryani → 4 plates total → 2000g rice
        when(orderRepository.findActiveOrdersBetween(anyString(), any(), any(), any()))
                .thenReturn(List.of(preOrder("o1", "2026-08-11"), preOrder("o2", "2026-08-11")));
        when(orderItemRepository.findByOrderIdIn(anyList())).thenReturn(List.of(
                item(BIRYANI, "Biryani", 2),
                item(BIRYANI, "Biryani", 2)));
        when(menuItemIngredientRepository.findByMenuItemIdIn(anyList())).thenReturn(List.of(
                recipe(BIRYANI, "Rice", "g", "500")));
        when(ingredientRepository.findByRestaurantIdOrderByNameAsc(RESTAURANT)).thenReturn(List.of());

        Map<String, Object> forecast = service.forecastForDate(RESTAURANT, LocalDate.of(2026, 8, 11));
        Map<String, Object> rice = find(cast(forecast.get("ingredients")), "Rice");
        assertEquals(0, new BigDecimal("2000").compareTo((BigDecimal) rice.get("requiredQuantity")));
        assertEquals(4, ((Map<String, Object>) ((List<?>) forecast.get("dishes")).get(0)).get("plates"));
    }

    @Test
    void dishWithoutRecipeProducesEmptyForecast() {
        when(orderRepository.findActiveOrdersBetween(anyString(), any(), any(), any()))
                .thenReturn(List.of(preOrder("o1", "2026-08-11")));
        when(orderItemRepository.findByOrderIdIn(anyList())).thenReturn(List.of(
                item(BIRYANI, "Biryani", 10)));
        when(menuItemIngredientRepository.findByMenuItemIdIn(anyList())).thenReturn(List.of());

        Map<String, Object> forecast = service.forecastForDate(RESTAURANT, LocalDate.of(2026, 8, 11));
        assertTrue(cast(forecast.get("ingredients")).isEmpty());
    }

    @Test
    void shortfallIsComputedAgainstStock() {
        when(orderRepository.findActiveOrdersBetween(anyString(), any(), any(), any()))
                .thenReturn(List.of(preOrder("o1", "2026-08-11")));
        when(orderItemRepository.findByOrderIdIn(anyList())).thenReturn(List.of(
                item(BIRYANI, "Biryani", 10)));
        when(menuItemIngredientRepository.findByMenuItemIdIn(anyList())).thenReturn(List.of(
                recipe(BIRYANI, "Rice", "g", "500")));
        when(ingredientRepository.findByRestaurantIdOrderByNameAsc(RESTAURANT)).thenReturn(List.of(
                Ingredient.builder().id("ING_1").name("Rice").unit("g")
                        .stockQuantity(new BigDecimal("3000")).reorderLevel(new BigDecimal("2000")).build()));

        Map<String, Object> forecast = service.forecastForDate(RESTAURANT, LocalDate.of(2026, 8, 11));
        Map<String, Object> rice = find(cast(forecast.get("ingredients")), "Rice");
        assertEquals(0, new BigDecimal("5000").compareTo((BigDecimal) rice.get("requiredQuantity")));
        assertEquals(0, new BigDecimal("2000").compareTo((BigDecimal) rice.get("shortfall")));
        assertEquals(true, rice.get("needPurchase"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cast(Object o) {
        return (List<Map<String, Object>>) o;
    }

    private static Map<String, Object> find(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElseThrow();
    }
}
