package com.savorystay.config;

import com.savorystay.entity.*;
import com.savorystay.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds the platform with a super admin, two demo restaurants,
 * their staff, menus (with ingredient recipes), stock and sample orders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemIngredientRepository menuItemIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final RestaurantOperatingHourRepository operatingHourRepository;
    private final PreOrderSettingsRepository preOrderSettingsRepository;
    private final DishAvailabilityRepository dishAvailabilityRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Pre-order defaults are seeded independently of the demo-data guard so
        // existing databases (already populated) still get sane defaults. The
        // method is idempotent — it only fills rows that are missing.
        seedPreOrderDefaults("REST_DEMO_1");
        seedPreOrderDefaults("REST_DEMO_2");

        if (userRepository.count() > 0) {
            log.info("DataSeeder: database already populated, skipping.");
            return;
        }

        log.info("DataSeeder: bootstrapping demo data...");

        // ============ PLATFORM SUPER ADMIN ============
        User superAdmin = userRepository.save(User.builder()
                .id("USR_SUPERADMIN")
                .username("superadmin")
                .email("superadmin@savorystay.com")
                .phone("+919999000001")
                .passwordHash(passwordEncoder.encode("SuperAdmin@123"))
                .role("ROLE_SUPER_ADMIN")
                .restaurantId(null)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());

        // ============ DEMO CUSTOMER ============
        userRepository.save(User.builder()
                .id("USR_CUSTOMER")
                .username("customer")
                .email("customer@savorystay.com")
                .phone("+919999000002")
                .passwordHash(passwordEncoder.encode("Customer@123"))
                .role("ROLE_CUSTOMER")
                .restaurantId(null)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());

        // ============ RESTAURANT 1: SAVORYSTAY FINE DINING ============
        Restaurant r1 = restaurantRepository.save(Restaurant.builder()
                .id("REST_DEMO_1")
                .name("SavoryStay Fine Dining")
                .description("Contemporary Indian fine-dining with a modern twist and 5-star service.")
                .address("42 Marine Drive, Bandra West")
                .city("Mumbai")
                .cuisine("Modern Indian")
                .phone("+91 22 4000 1000")
                .email("reservations@savorystaydining.com")
                .logoUrl("https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&q=80&w=800")
                .currency("INR")
                .status("ACTIVE")
                .ownerId(superAdmin.getId())
                .createdAt(LocalDateTime.now())
                .build());

        userRepository.save(User.builder()
                .id("USR_R1_ADMIN").username("savoryadmin").email("admin@savorystaydining.com")
                .phone("+919999000011").passwordHash(passwordEncoder.encode("Admin@123"))
                .role("ROLE_ADMIN").restaurantId(r1.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder()
                .id("USR_R1_MGR").username("savorymanager").email("manager@savorystaydining.com")
                .phone("+919999000012").passwordHash(passwordEncoder.encode("Manager@123"))
                .role("ROLE_MANAGER").restaurantId(r1.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder()
                .id("USR_R1_CHEF").username("savorychef").email("chef@savorystaydining.com")
                .phone("+919999000013").passwordHash(passwordEncoder.encode("Chef@123"))
                .role("ROLE_CHEF").restaurantId(r1.getId()).enabled(true).createdAt(LocalDateTime.now()).build());

        seedMenu(r1.getId(), "REST_DEMO_1");
        seedStock(r1.getId(), "REST_DEMO_1");
        seedSampleOrders(r1.getId());
        seedPriceRules(r1.getId(), "REST_DEMO_1");

        // ============ RESTAURANT 2: SPICE GARDEN ============
        Restaurant r2 = restaurantRepository.save(Restaurant.builder()
                .id("REST_DEMO_2")
                .name("Spice Garden")
                .description("Authentic regional Indian cuisine in a relaxed garden setting.")
                .address("88 Jubilee Hills Road")
                .city("Hyderabad")
                .cuisine("Hyderabadi & Mughlai")
                .phone("+91 40 2333 2000")
                .email("hello@spicegarden.in")
                .logoUrl("https://images.unsplash.com/photo-1552566626-52f8b828add9?auto=format&fit=crop&q=80&w=800")
                .currency("INR")
                .status("ACTIVE")
                .ownerId(superAdmin.getId())
                .createdAt(LocalDateTime.now())
                .build());

        userRepository.save(User.builder()
                .id("USR_R2_ADMIN").username("spiceadmin").email("admin@spicegarden.in")
                .phone("+919999000021").passwordHash(passwordEncoder.encode("Admin@123"))
                .role("ROLE_ADMIN").restaurantId(r2.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder()
                .id("USR_R2_MGR").username("spicemanager").email("manager@spicegarden.in")
                .phone("+919999000022").passwordHash(passwordEncoder.encode("Manager@123"))
                .role("ROLE_MANAGER").restaurantId(r2.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder()
                .id("USR_R2_CHEF").username("spicechef").email("chef@spicegarden.in")
                .phone("+919999000023").passwordHash(passwordEncoder.encode("Chef@123"))
                .role("ROLE_CHEF").restaurantId(r2.getId()).enabled(true).createdAt(LocalDateTime.now()).build());

        seedMenu(r2.getId(), "REST_DEMO_2");
        seedStock(r2.getId(), "REST_DEMO_2");
        seedSampleOrders(r2.getId());
        seedPriceRules(r2.getId(), "REST_DEMO_2");

        log.info("DataSeeder: done. Login: superadmin / SuperAdmin@123");
    }

    /**
     * Seeds pre-order configuration (operating hours, cutoff settings, dish
     * availability) for a restaurant if it does not already have them.
     *
     * Demo model: Mon + Sat close at 14:00 ("2nd half closed"), Sunday is a
     * weekly holiday, other days 09:00–23:00. Cutoff 09:00 on D-1, 7-day
     * horizon. Butter Chicken is only cooked Mon/Wed/Fri/Sun; Masala Chai is
     * available every day; the rest of the menu is unconfigured (= daily,
     * backward compatible).
     */
    private void seedPreOrderDefaults(String restaurantId) {
        if (operatingHourRepository.findByRestaurantId(restaurantId).isEmpty()) {
            List<RestaurantOperatingHour> hours = List.of(
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(1)
                            .openTime(java.time.LocalTime.of(9, 0)).closeTime(java.time.LocalTime.of(14, 0)).closed(false).build(), // Mon 2nd half closed
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(2)
                            .openTime(java.time.LocalTime.of(9, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(3)
                            .openTime(java.time.LocalTime.of(9, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(4)
                            .openTime(java.time.LocalTime.of(9, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(5)
                            .openTime(java.time.LocalTime.of(9, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(6)
                            .openTime(java.time.LocalTime.of(9, 0)).closeTime(java.time.LocalTime.of(14, 0)).closed(false).build(), // Sat 2nd half closed
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(7)
                            .closed(true).build() // Sunday weekly holiday
            );
            operatingHourRepository.saveAll(hours);
            log.info("DataSeeder: operating hours seeded for {}", restaurantId);
        }

        if (preOrderSettingsRepository.findByRestaurantId(restaurantId).isEmpty()) {
            preOrderSettingsRepository.save(PreOrderSettings.builder()
                    .restaurantId(restaurantId)
                    .cutoffTime(java.time.LocalTime.of(9, 0))
                    .advanceDays(7)
                    .build());
            log.info("DataSeeder: pre-order settings seeded for {}", restaurantId);
        }

        if (dishAvailabilityRepository.findByRestaurantId(restaurantId).isEmpty()) {
            String prefix = restaurantId;
            List<DishAvailability> avail = List.of(
                    // Butter Chicken cooked Mon/Wed/Fri/Sun
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_1").dayOfWeek(1).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_1").dayOfWeek(3).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_1").dayOfWeek(5).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_1").dayOfWeek(7).build(),
                    // Masala Chai available every day
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_5").dayOfWeek(1).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_5").dayOfWeek(2).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_5").dayOfWeek(3).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_5").dayOfWeek(4).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_5").dayOfWeek(5).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_5").dayOfWeek(6).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(prefix + "_MI_5").dayOfWeek(7).build()
            );
            dishAvailabilityRepository.saveAll(avail);
            log.info("DataSeeder: dish availability seeded for {}", restaurantId);
        }
    }

    private void seedMenu(String restaurantId, String prefix) {
        List<MenuItem> items = List.of(
                MenuItem.builder().id(prefix + "_MI_1").restaurantId(restaurantId)
                        .title("Butter Chicken").description("Tandoor-roasted chicken in rich tomato-butter gravy.")
                        .price(new BigDecimal("420")).category("Mains").status("Available").isVeg(false)
                        .spiceLevel("Medium").prepMinutes(18)
                        .imageUrl("https://images.unsplash.com/photo-1603894584373-5ac82b2ae398?auto=format&fit=crop&q=80&w=800").build(),
                MenuItem.builder().id(prefix + "_MI_2").restaurantId(restaurantId)
                        .title("Paneer Tikka").description("Char-grilled cottage cheese with mint chutney.")
                        .price(new BigDecimal("320")).category("Starters").status("Available").isVeg(true)
                        .spiceLevel("Spicy").prepMinutes(12)
                        .imageUrl("https://images.unsplash.com/photo-1567188040759-fb8a883dc6d8?auto=format&fit=crop&q=80&w=800").build(),
                MenuItem.builder().id(prefix + "_MI_3").restaurantId(restaurantId)
                        .title("Garlic Naan").description("Stone-oven flatbread brushed with garlic butter.")
                        .price(new BigDecimal("80")).category("Breads").status("Available").isVeg(true)
                        .spiceLevel("Mild").prepMinutes(5)
                        .imageUrl("https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&q=80&w=800").build(),
                MenuItem.builder().id(prefix + "_MI_4").restaurantId(restaurantId)
                        .title("Gulab Jamun").description("Warm milk dumplings in rose-cardamom syrup.")
                        .price(new BigDecimal("150")).category("Desserts").status("Available").isVeg(true)
                        .spiceLevel("Mild").prepMinutes(8)
                        .imageUrl("https://images.unsplash.com/photo-1589118949245-7d38baf380d6?auto=format&fit=crop&q=80&w=800").build(),
                MenuItem.builder().id(prefix + "_MI_5").restaurantId(restaurantId)
                        .title("Masala Chai").description("Spiced Assam tea brewed with milk.")
                        .price(new BigDecimal("120")).category("Beverages").status("Available").isVeg(true)
                        .spiceLevel("Medium").prepMinutes(4)
                        .imageUrl("https://images.unsplash.com/photo-1571934811356-5cc061b6821f?auto=format&fit=crop&q=80&w=800").build()
        );
        menuItemRepository.saveAll(items);

        // Recipes: [menuItemId, ingredientName, qtyPerUnit, unit]
        List<MenuItemIngredient> recipes = List.of(
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_1").restaurantId(restaurantId)
                        .name("Chicken").quantityPerUnit(new BigDecimal("250")).unit("g").build(),
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_1").restaurantId(restaurantId)
                        .name("Butter").quantityPerUnit(new BigDecimal("50")).unit("g").build(),
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_1").restaurantId(restaurantId)
                        .name("Fresh Cream").quantityPerUnit(new BigDecimal("100")).unit("ml").build(),
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_1").restaurantId(restaurantId)
                        .name("Tomato Puree").quantityPerUnit(new BigDecimal("150")).unit("g").build(),

                MenuItemIngredient.builder().menuItemId(prefix + "_MI_2").restaurantId(restaurantId)
                        .name("Paneer").quantityPerUnit(new BigDecimal("200")).unit("g").build(),
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_2").restaurantId(restaurantId)
                        .name("Yogurt").quantityPerUnit(new BigDecimal("60")).unit("g").build(),

                MenuItemIngredient.builder().menuItemId(prefix + "_MI_3").restaurantId(restaurantId)
                        .name("Wheat Flour").quantityPerUnit(new BigDecimal("80")).unit("g").build(),
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_3").restaurantId(restaurantId)
                        .name("Butter").quantityPerUnit(new BigDecimal("15")).unit("g").build(),

                MenuItemIngredient.builder().menuItemId(prefix + "_MI_4").restaurantId(restaurantId)
                        .name("Milk Powder").quantityPerUnit(new BigDecimal("50")).unit("g").build(),
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_4").restaurantId(restaurantId)
                        .name("Sugar").quantityPerUnit(new BigDecimal("40")).unit("g").build(),

                MenuItemIngredient.builder().menuItemId(prefix + "_MI_5").restaurantId(restaurantId)
                        .name("Tea Leaves").quantityPerUnit(new BigDecimal("5")).unit("g").build(),
                MenuItemIngredient.builder().menuItemId(prefix + "_MI_5").restaurantId(restaurantId)
                        .name("Milk").quantityPerUnit(new BigDecimal("150")).unit("ml").build()
        );
        menuItemIngredientRepository.saveAll(recipes);
    }

    private void seedStock(String restaurantId, String prefix) {
        List<Ingredient> stock = List.of(
                Ingredient.builder().id(prefix + "_ING_1").restaurantId(restaurantId).name("Chicken")
                        .unit("g").stockQuantity(new BigDecimal("15000")).reorderLevel(new BigDecimal("5000")).build(),
                Ingredient.builder().id(prefix + "_ING_2").restaurantId(restaurantId).name("Butter")
                        .unit("g").stockQuantity(new BigDecimal("8000")).reorderLevel(new BigDecimal("2000")).build(),
                Ingredient.builder().id(prefix + "_ING_3").restaurantId(restaurantId).name("Fresh Cream")
                        .unit("ml").stockQuantity(new BigDecimal("10000")).reorderLevel(new BigDecimal("3000")).build(),
                Ingredient.builder().id(prefix + "_ING_4").restaurantId(restaurantId).name("Tomato Puree")
                        .unit("g").stockQuantity(new BigDecimal("9000")).reorderLevel(new BigDecimal("4000")).build(),
                Ingredient.builder().id(prefix + "_ING_5").restaurantId(restaurantId).name("Paneer")
                        .unit("g").stockQuantity(new BigDecimal("6000")).reorderLevel(new BigDecimal("2000")).build(),
                Ingredient.builder().id(prefix + "_ING_6").restaurantId(restaurantId).name("Yogurt")
                        .unit("g").stockQuantity(new BigDecimal("5000")).reorderLevel(new BigDecimal("1500")).build(),
                Ingredient.builder().id(prefix + "_ING_7").restaurantId(restaurantId).name("Wheat Flour")
                        .unit("g").stockQuantity(new BigDecimal("20000")).reorderLevel(new BigDecimal("6000")).build(),
                Ingredient.builder().id(prefix + "_ING_8").restaurantId(restaurantId).name("Milk Powder")
                        .unit("g").stockQuantity(new BigDecimal("7000")).reorderLevel(new BigDecimal("2000")).build(),
                Ingredient.builder().id(prefix + "_ING_9").restaurantId(restaurantId).name("Sugar")
                        .unit("g").stockQuantity(new BigDecimal("9000")).reorderLevel(new BigDecimal("3000")).build(),
                Ingredient.builder().id(prefix + "_ING_10").restaurantId(restaurantId).name("Tea Leaves")
                        .unit("g").stockQuantity(new BigDecimal("2500")).reorderLevel(new BigDecimal("800")).build(),
                Ingredient.builder().id(prefix + "_ING_11").restaurantId(restaurantId).name("Milk")
                        .unit("ml").stockQuantity(new BigDecimal("20000")).reorderLevel(new BigDecimal("5000")).build()
        );
        ingredientRepository.saveAll(stock);
    }

    /**
     * Demo scheduled pricing: one rule effective immediately (price bump),
     * one scheduled 7 days out. The menu & checkout both use the effective price.
     */
    private void seedPriceRules(String restaurantId, String prefix) {
        priceRuleRepository.save(PriceRule.builder()
                .menuItemId(prefix + "_MI_1")
                .price(new BigDecimal("450"))
                .effectiveFrom(LocalDateTime.now().minusDays(1))
                .build());
        priceRuleRepository.save(PriceRule.builder()
                .menuItemId(prefix + "_MI_3")
                .price(new BigDecimal("90"))
                .effectiveFrom(LocalDateTime.now().plusDays(7))
                .build());
        log.info("DataSeeder: price rules seeded for {}", restaurantId);
    }

    private void seedSampleOrders(String restaurantId) {
        String prefix = restaurantId.equals("REST_DEMO_1") ? "REST_DEMO_1" : "REST_DEMO_2";

        Order o1 = orderRepository.save(Order.builder()
                .id(prefix + "_ORD_1")
                .orderNumber("#ORD-DEMO1")
                .restaurantId(restaurantId)
                .orderType("PICKUP")
                .timeSlot("12:30")
                .pickupTime("12:30")
                .customerName("Demo Customer")
                .customerPhone("+919999000002")
                .customerEmail("customer@savorystay.com")
                .userId("USR_CUSTOMER")
                .totalAmount(new BigDecimal("840"))
                .paymentStatus("PAID")
                .paymentMethod("UPI")
                .orderStatus("NEW")
                .createdAt(LocalDateTime.now().minusMinutes(45))
                .build());
        orderItemRepository.saveAll(List.of(
                OrderItem.builder().id(prefix + "_OI_1").orderId(o1.getId()).menuItemId(prefix + "_MI_1")
                        .title("Butter Chicken").quantity(2).unitPrice(new BigDecimal("420")).build(),
                OrderItem.builder().id(prefix + "_OI_2").orderId(o1.getId()).menuItemId(prefix + "_MI_3")
                        .title("Garlic Naan").quantity(2).unitPrice(new BigDecimal("80")).build()
        ));

        Order o2 = orderRepository.save(Order.builder()
                .id(prefix + "_ORD_2")
                .orderNumber("#ORD-DEMO2")
                .restaurantId(restaurantId)
                .orderType("DINE_IN")
                .tableNumber(7)
                .guests(2)
                .timeSlot("14:00")
                .customerName("Walk-in Guest")
                .totalAmount(new BigDecimal("470"))
                .paymentStatus("PAID")
                .paymentMethod("CARD")
                .orderStatus("PREPARING")
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .build());
        orderItemRepository.saveAll(List.of(
                OrderItem.builder().id(prefix + "_OI_3").orderId(o2.getId()).menuItemId(prefix + "_MI_2")
                        .title("Paneer Tikka").quantity(1).unitPrice(new BigDecimal("320")).build(),
                OrderItem.builder().id(prefix + "_OI_4").orderId(o2.getId()).menuItemId(prefix + "_MI_4")
                        .title("Gulab Jamun").quantity(1).unitPrice(new BigDecimal("150")).build()
        ));

        Order o3 = orderRepository.save(Order.builder()
                .id(prefix + "_ORD_3")
                .orderNumber("#ORD-DEMO3")
                .restaurantId(restaurantId)
                .orderType("PICKUP")
                .timeSlot("18:45")
                .pickupTime("18:45")
                .customerName("Demo Customer")
                .customerPhone("+919999000002")
                .customerEmail("customer@savorystay.com")
                .userId("USR_CUSTOMER")
                .totalAmount(new BigDecimal("240"))
                .paymentStatus("PAID")
                .paymentMethod("MOCK")
                .orderStatus("PACKED_READY")
                .createdAt(LocalDateTime.now().minusHours(3))
                .build());
        orderItemRepository.saveAll(List.of(
                OrderItem.builder().id(prefix + "_OI_5").orderId(o3.getId()).menuItemId(prefix + "_MI_5")
                        .title("Masala Chai").quantity(2).unitPrice(new BigDecimal("120")).build()
        ));
    }
}
