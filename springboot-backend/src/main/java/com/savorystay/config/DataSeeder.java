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
import java.util.UUID;

/**
 * Seeds the platform with a super admin, two demo restaurants,
 * their staff, menus (with ingredient recipes), stock and sample orders.
 *
 * LIVE DEMO: Seeded with 25+ menu items, 25+ ingredients, 30+ orders
 * per restaurant across every status so dashboards look rich on launch.
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
    private final CustomerRestaurantRepository customerRestaurantRepository;
    private final NotificationRepository notificationRepository;
    private final RestaurantSettingsRepository restaurantSettingsRepository;

    @Override
    public void run(String... args) {
        // Pre-order defaults are seeded independently of the demo-data guard so
        // existing databases (already populated) still get sane defaults.
        seedPreOrderDefaults("REST_DEMO_1");
        seedPreOrderDefaults("REST_DEMO_2");

        if (userRepository.count() > 0) {
            log.info("DataSeeder: database already populated, skipping.");
            return;
        }

        log.info("DataSeeder: bootstrapping demo data...");

        // ============ PLATFORM SUPER ADMIN ============
        userRepository.save(User.builder()
                .id("USR_SUPERADMIN").username("superadmin").email("superadmin@savorystay.com")
                .phone("+919999000001").passwordHash(passwordEncoder.encode("SuperAdmin@123"))
                .role("ROLE_SUPER_ADMIN").restaurantId(null).enabled(true).createdAt(LocalDateTime.now()).build());

        // ============ DEMO CUSTOMERS (8) ============
        saveCustomer("USR_CUSTOMER", "customer", "customer@savorystay.com", "+919999000002");
        saveCustomer("USR_CUST_2", "priya_sharma", "priya@example.com", "+919876543210");
        saveCustomer("USR_CUST_3", "rahul_mehta", "rahul.m@example.com", "+919812345678");
        saveCustomer("USR_CUST_4", "ananya_verma", "ananya@example.com", "+919765432109");
        saveCustomer("USR_CUST_5", "vikram_patel", "vikram@example.com", "+919654321098");
        saveCustomer("USR_CUST_6", "neha_gupta", "neha@example.com", "+919543210987");
        saveCustomer("USR_CUST_7", "arjun_singh", "arjun@example.com", "+919432109876");
        saveCustomer("USR_CUST_8", "meera_iyer", "meera@example.com", "+919321098765");

        // ============ RESTAURANT 1: SAVORYSTAY FINE DINING ============
        Restaurant r1 = restaurantRepository.save(Restaurant.builder()
                .id("REST_DEMO_1").name("SavoryStay Fine Dining")
                .description("Contemporary Indian fine-dining with a modern twist and 5-star service.")
                .address("42 Marine Drive, Bandra West").city("Mumbai").cuisine("Modern Indian")
                .phone("+91 22 4000 1000").email("reservations@savorystaydining.com")
                .logoUrl("https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&q=80&w=800")
                .currency("INR").status("ACTIVE").ownerId("USR_SUPERADMIN").createdAt(LocalDateTime.now()).build());

        userRepository.save(User.builder().id("USR_R1_ADMIN").username("savoryadmin").email("admin@savorystaydining.com")
                .phone("+919999000011").passwordHash(passwordEncoder.encode("Admin@123"))
                .role("ROLE_ADMIN").restaurantId(r1.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder().id("USR_R1_MGR").username("savorymanager").email("manager@savorystaydining.com")
                .phone("+919999000012").passwordHash(passwordEncoder.encode("Manager@123"))
                .role("ROLE_MANAGER").restaurantId(r1.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder().id("USR_R1_CHEF").username("savorychef").email("chef@savorystaydining.com")
                .phone("+919999000013").passwordHash(passwordEncoder.encode("Chef@123"))
                .role("ROLE_CHEF").restaurantId(r1.getId()).enabled(true).createdAt(LocalDateTime.now()).build());

        seedMenuV2(r1.getId(), "REST_DEMO_1");
        seedStockV2(r1.getId(), "REST_DEMO_1");
        seedSampleOrdersV2(r1.getId(), "REST_DEMO_1");
        seedPriceRulesV2(r1.getId(), "REST_DEMO_1");

        // ============ RESTAURANT 2: SPICE GARDEN ============
        Restaurant r2 = restaurantRepository.save(Restaurant.builder()
                .id("REST_DEMO_2").name("Spice Garden")
                .description("Authentic regional Indian cuisine in a relaxed garden setting.")
                .address("88 Jubilee Hills Road").city("Hyderabad").cuisine("Hyderabadi & Mughlai")
                .phone("+91 40 2333 2000").email("hello@spicegarden.in")
                .logoUrl("https://images.unsplash.com/photo-1552566626-52f8b828add9?auto=format&fit=crop&q=80&w=800")
                .currency("INR").status("ACTIVE").ownerId("USR_SUPERADMIN").createdAt(LocalDateTime.now()).build());

        userRepository.save(User.builder().id("USR_R2_ADMIN").username("spiceadmin").email("admin@spicegarden.in")
                .phone("+919999000021").passwordHash(passwordEncoder.encode("Admin@123"))
                .role("ROLE_ADMIN").restaurantId(r2.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder().id("USR_R2_MGR").username("spicemanager").email("manager@spicegarden.in")
                .phone("+919999000022").passwordHash(passwordEncoder.encode("Manager@123"))
                .role("ROLE_MANAGER").restaurantId(r2.getId()).enabled(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder().id("USR_R2_CHEF").username("spicechef").email("chef@spicegarden.in")
                .phone("+919999000023").passwordHash(passwordEncoder.encode("Chef@123"))
                .role("ROLE_CHEF").restaurantId(r2.getId()).enabled(true).createdAt(LocalDateTime.now()).build());

        seedMenuV2(r2.getId(), "REST_DEMO_2");
        seedStockV2(r2.getId(), "REST_DEMO_2");
        seedSampleOrdersV2(r2.getId(), "REST_DEMO_2");
        seedPriceRulesV2(r2.getId(), "REST_DEMO_2");

        // ============ RESTAURANT SETTINGS (tables & time slots) ============
        seedRestaurantSettings();

        // ============ CUSTOMER–RESTAURANT MEMBERSHIPS ============
        seedMemberships();

        // ============ NOTIFICATIONS ============
        seedNotifications();

        log.info("DataSeeder: done. Login: superadmin / SuperAdmin@123");
        log.info("DataSeeder: 8 customers, 2 restaurants, 50 menu items, 60+ orders seeded.");
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────

    private void saveCustomer(String id, String username, String email, String phone) {
        userRepository.save(User.builder().id(id).username(username).email(email)
                .phone(phone).passwordHash(passwordEncoder.encode("Customer@123"))
                .role("ROLE_CUSTOMER").restaurantId(null).enabled(true).createdAt(LocalDateTime.now()).build());
    }

    // ─── PRE-ORDER DEFAULTS ──────────────────────────────────────────────

    private void seedPreOrderDefaults(String restaurantId) {
        if (operatingHourRepository.findByRestaurantId(restaurantId).isEmpty()) {
            List<RestaurantOperatingHour> hours = List.of(
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(1)
                            .openTime(java.time.LocalTime.of(11, 0)).closeTime(java.time.LocalTime.of(15, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(2)
                            .openTime(java.time.LocalTime.of(11, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(3)
                            .openTime(java.time.LocalTime.of(11, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(4)
                            .openTime(java.time.LocalTime.of(11, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(5)
                            .openTime(java.time.LocalTime.of(11, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(6)
                            .openTime(java.time.LocalTime.of(11, 0)).closeTime(java.time.LocalTime.of(23, 0)).closed(false).build(),
                    RestaurantOperatingHour.builder().restaurantId(restaurantId).dayOfWeek(7).closed(true).build()
            );
            operatingHourRepository.saveAll(hours);
        }
        if (preOrderSettingsRepository.findByRestaurantId(restaurantId).isEmpty()) {
            preOrderSettingsRepository.save(PreOrderSettings.builder()
                    .restaurantId(restaurantId).cutoffTime(java.time.LocalTime.of(9, 0)).advanceDays(7).build());
        }
        if (dishAvailabilityRepository.findByRestaurantId(restaurantId).isEmpty()) {
            String p = restaurantId;
            List<DishAvailability> avail = List.of(
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_1").dayOfWeek(1).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_1").dayOfWeek(3).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_1").dayOfWeek(5).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_15").dayOfWeek(1).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_15").dayOfWeek(2).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_15").dayOfWeek(3).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_15").dayOfWeek(4).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_15").dayOfWeek(5).build(),
                    DishAvailability.builder().restaurantId(restaurantId).menuItemId(p + "_MI_15").dayOfWeek(6).build()
            );
            dishAvailabilityRepository.saveAll(avail);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  MENU V2 — 25 items per restaurant, realistic Indian fine-dining
    // ═══════════════════════════════════════════════════════════════════════

    private void seedMenuV2(String restaurantId, String p) {
        List<MenuItem> items = List.of(
                // ── STARTERS (6) ──
                mi(p+"_MI_1",  restaurantId, "Butter Chicken",      "Tandoor-roasted chicken in rich tomato-butter gravy with kasuri methi.",    "420", "Mains",    false, "Medium",    18, 30),
                mi(p+"_MI_2",  restaurantId, "Paneer Tikka",        "Char-grilled cottage cheese marinated in yogurt and spices with mint chutney.","320", "Starters", true,  "Spicy",     12, 40),
                mi(p+"_MI_3",  restaurantId, "Garlic Naan",         "Stone-oven flatbread brushed with garlic butter and fresh coriander.",       "80",  "Breads",   true,  "Mild",      5,  60),
                mi(p+"_MI_4",  restaurantId, "Gulab Jamun",         "Warm milk dumplings in rose-cardamom syrup topped with crushed pistachios.", "150", "Desserts", true,  "Mild",      8,  25),
                mi(p+"_MI_5",  restaurantId, "Masala Chai",         "Spiced Assam tea brewed with cardamom, ginger, and fresh milk.",            "60",  "Beverages",true,  "Mild",      4,  80),
                mi(p+"_MI_6",  restaurantId, "Chicken Seekh Kebab", "Minced chicken skewered with herbs and spices, charcoal-grilled.",          "280", "Starters", false, "Medium",    15, 35),
                mi(p+"_MI_7",  restaurantId, "Crispy Paneer 65",    "Deep-fried paneer tossed with curry leaves, red chilies, and yogurt.",       "260", "Starters", true,  "Spicy",     12, 40),
                mi(p+"_MI_8",  restaurantId, "Tandoori Prawns",     "Jumbo prawns marinated in yogurt and tandoori spices, flame-grilled.",       "450", "Starters", false, "Medium",    14, 20),
                // ── MAINS (7) ──
                mi(p+"_MI_9",  restaurantId, "Dal Makhani",         "Overnight slow-cooked black lentils enriched with white butter and cream.",  "260", "Mains",    true,  "Mild",      10, 50),
                mi(p+"_MI_10", restaurantId, "Mutton Rogan Josh",   "Kashmiri slow-braised lamb in aromatic red chili and fennel gravy.",        "480", "Mains",    false, "Fiery Hot", 25, 25),
                mi(p+"_MI_11", restaurantId, "Palak Paneer",        "Creamed spinach slow-cooked with soft cottage cheese cubes.",               "240", "Mains",    true,  "Medium",    12, 35),
                mi(p+"_MI_12", restaurantId, "Hyderabadi Biryani",  "Fragrant basmati rice layered with saffron, tender goat, and fried onions.","520", "Mains",    false, "Spicy",     22, 20),
                mi(p+"_MI_13", restaurantId, "Chole Bhature",       "Spiced chickpea curry served with fluffy deep-fried bread.",               "180", "Mains",    true,  "Medium",    12, 45),
                mi(p+"_MI_14", restaurantId, "Fish Amritsari",      "Crispy gram-flour battered river fish with tangy tamarind chutney.",       "380", "Mains",    false, "Medium",    15, 25),
                mi(p+"_MI_15", restaurantId, "Veg Biryani",         "Fragrant basmati rice layered with saffron, mixed vegetables, and raita.",  "300", "Mains",    true,  "Mild",      18, 40),
                // ── BREADS (4) ──
                mi(p+"_MI_16", restaurantId, "Butter Naan",         "Soft leavened flatbread baked in tandoor, brushed with melted butter.",     "70",  "Breads",   true,  "Mild",      5,  70),
                mi(p+"_MI_17", restaurantId, "Cheese Garlic Naan",  "Naan stuffed with mozzarella cheese and roasted garlic.",                  "120", "Breads",   true,  "Mild",      6,  50),
                mi(p+"_MI_18", restaurantId, "Laccha Paratha",      "Flaky multi-layered whole wheat bread, pan-fried with ghee.",              "90",  "Breads",   true,  "Mild",      6,  55),
                mi(p+"_MI_19", restaurantId, "Missi Roti",          "Gram flour flatbread seasoned with ajwain and fenugreek leaves.",           "75",  "Breads",   true,  "Medium",    5,  50),
                // ── DESSERTS (4) ──
                mi(p+"_MI_20", restaurantId, "Rasmalai",            "Delicate cottage cheese patties soaked in saffron-cardamom milk.",         "180", "Desserts", true,  "Mild",      5,  25),
                mi(p+"_MI_21", restaurantId, "Kulfi Falooda",       "Traditional Indian ice cream with vermicelli, rose syrup, and pistachios.", "160", "Desserts", true,  "Mild",      4,  30),
                mi(p+"_MI_22", restaurantId, "Gajar Ka Halwa",      "Carrot pudding slow-cooked in milk, ghee, and cardamom, topped with nuts.", "140", "Desserts", true,  "Mild",      8,  20),
                mi(p+"_MI_23", restaurantId, "Phirni",              "Creamy ground rice pudding set in earthenware with rose petals.",          "130", "Desserts", true,  "Mild",      5,  25),
                // ── BEVERAGES (2) ──
                mi(p+"_MI_24", restaurantId, "Mango Lassi",         "Creamy yogurt shake blended with Alphonso mango pulp and cardamom.",       "120", "Beverages",true,  "Mild",      3,  60),
                mi(p+"_MI_25", restaurantId, "Cold Coffee",         "Iced coffee blended with vanilla ice cream, cocoa dust, and cream.",       "150", "Beverages",true,  "Mild",      3,  50)
        );
        menuItemRepository.saveAll(items);
        log.info("DataSeeder: {} menu items seeded for {}", items.size(), restaurantId);
    }

    /** Helper to build a MenuItem with plate count. */
    private MenuItem mi(String id, String restId, String title, String desc, String price,
                        String cat, boolean veg, String spice, int prep, int plates) {
        return MenuItem.builder().id(id).restaurantId(restId).title(title).description(desc)
                .price(new BigDecimal(price)).category(cat).status("Available").isVeg(veg)
                .spiceLevel(spice).prepMinutes(prep).dailyPlateCount(plates)
                .imageUrl("https://images.unsplash.com/photo-1603894584373-5ac82b2ae398?auto=format&fit=crop&q=80&w=800")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STOCK V2 — 25+ ingredients per restaurant, some low for alerts
    // ═══════════════════════════════════════════════════════════════════════

    private void seedStockV2(String restaurantId, String p) {
        List<Ingredient> stock = List.of(
                ing(p+"_ING_1",  restaurantId, "Chicken",       "g",   "18000", "5000"),
                ing(p+"_ING_2",  restaurantId, "Mutton",        "g",    "8000", "3000"),
                ing(p+"_ING_3",  restaurantId, "Paneer",        "g",   "10000", "3000"),
                ing(p+"_ING_4",  restaurantId, "Prawns",        "g",    "4000", "1500"),
                ing(p+"_ING_5",  restaurantId, "Fish",          "g",    "5000", "2000"),
                ing(p+"_ING_6",  restaurantId, "Butter",        "g",   "12000", "3000"),
                ing(p+"_ING_7",  restaurantId, "Fresh Cream",   "ml",  "10000", "3000"),
                ing(p+"_ING_8",  restaurantId, "Tomato Puree",  "g",   "15000", "5000"),
                ing(p+"_ING_9",  restaurantId, "Yogurt",        "g",    "8000", "2500"),
                ing(p+"_ING_10", restaurantId, "Wheat Flour",   "g",   "25000", "8000"),
                ing(p+"_ING_11", restaurantId, "Basmati Rice",  "g",   "30000", "10000"),
                ing(p+"_ING_12", restaurantId, "Milk",          "ml",  "25000", "8000"),
                ing(p+"_ING_13", restaurantId, "Sugar",         "g",   "12000", "4000"),
                ing(p+"_ING_14", restaurantId, "Tea Leaves",    "g",    "3000", "1000"),
                ing(p+"_ING_15", restaurantId, "Milk Powder",   "g",    "6000", "2000"),
                ing(p+"_ING_16", restaurantId, "Onions",        "g",   "15000", "5000"),
                ing(p+"_ING_17", restaurantId, "Garlic",        "g",    "4000", "1500"),
                ing(p+"_ING_18", restaurantId, "Ginger",        "g",    "3500", "1200"),
                ing(p+"_ING_19", restaurantId, "Green Chilies", "g",    "2500", "1000"),
                ing(p+"_ING_20", restaurantId, "Cumin Seeds",   "g",    "1500", "500"),
                // ── Intentionally LOW stock for demo alerts ──
                ing(p+"_ING_21", restaurantId, "Saffron",       "g",      "40", "100"),
                ing(p+"_ING_22", restaurantId, "Cardamom",      "g",      "60", "150"),
                ing(p+"_ING_23", restaurantId, "Cashews",       "g",     "300", "800"),
                ing(p+"_ING_24", restaurantId, "Almonds",       "g",     "400", "600"),
                ing(p+"_ING_25", restaurantId, "Rose Water",    "ml",    "100", "250"),
                ing(p+"_ING_26", restaurantId, "Fenugreek",     "g",     "200", "500")
        );
        ingredientRepository.saveAll(stock);
        log.info("DataSeeder: {} ingredients seeded for {}", stock.size(), restaurantId);
    }

    private Ingredient ing(String id, String restId, String name, String unit, String stock, String reorder) {
        return Ingredient.builder().id(id).restaurantId(restId).name(name).unit(unit)
                .stockQuantity(new BigDecimal(stock)).reorderLevel(new BigDecimal(reorder)).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  RECIPES V2 — Every dish has a recipe (ingredient per plate)
    // ═══════════════════════════════════════════════════════════════════════

    private void seedRecipesV2(String restaurantId, String p) {
        List<MenuItemIngredient> recipes = List.of(
                // Butter Chicken
                r(p+"_MI_1", restaurantId, "Chicken",       "250", "g"),
                r(p+"_MI_1", restaurantId, "Butter",         "50", "g"),
                r(p+"_MI_1", restaurantId, "Fresh Cream",   "100", "ml"),
                r(p+"_MI_1", restaurantId, "Tomato Puree",  "150", "g"),
                // Paneer Tikka
                r(p+"_MI_2", restaurantId, "Paneer",        "200", "g"),
                r(p+"_MI_2", restaurantId, "Yogurt",         "60", "g"),
                r(p+"_MI_2", restaurantId, "Green Chilies",  "10", "g"),
                // Garlic Naan
                r(p+"_MI_3", restaurantId, "Wheat Flour",    "80", "g"),
                r(p+"_MI_3", restaurantId, "Butter",         "15", "g"),
                r(p+"_MI_3", restaurantId, "Garlic",          "8", "g"),
                // Gulab Jamun
                r(p+"_MI_4", restaurantId, "Milk Powder",    "50", "g"),
                r(p+"_MI_4", restaurantId, "Sugar",          "40", "g"),
                // Masala Chai
                r(p+"_MI_5", restaurantId, "Tea Leaves",      "5", "g"),
                r(p+"_MI_5", restaurantId, "Milk",          "150", "ml"),
                r(p+"_MI_5", restaurantId, "Ginger",          "5", "g"),
                // Chicken Seekh Kebab
                r(p+"_MI_6", restaurantId, "Chicken",       "200", "g"),
                r(p+"_MI_6", restaurantId, "Yogurt",         "40", "g"),
                r(p+"_MI_6", restaurantId, "Cumin Seeds",     "3", "g"),
                // Crispy Paneer 65
                r(p+"_MI_7", restaurantId, "Paneer",        "200", "g"),
                r(p+"_MI_7", restaurantId, "Yogurt",         "30", "g"),
                // Tandoori Prawns
                r(p+"_MI_8", restaurantId, "Prawns",        "250", "g"),
                r(p+"_MI_8", restaurantId, "Yogurt",         "50", "g"),
                r(p+"_MI_8", restaurantId, "Saffron",         "1", "g"),
                // Dal Makhani
                r(p+"_MI_9", restaurantId, "Wheat Flour",   "150", "g"),
                r(p+"_MI_9", restaurantId, "Butter",          "30", "g"),
                r(p+"_MI_9", restaurantId, "Fresh Cream",    "50", "ml"),
                // Mutton Rogan Josh
                r(p+"_MI_10", restaurantId, "Mutton",       "300", "g"),
                r(p+"_MI_10", restaurantId, "Yogurt",         "50", "g"),
                r(p+"_MI_10", restaurantId, "Cardamom",        "3", "g"),
                // Palak Paneer
                r(p+"_MI_11", restaurantId, "Paneer",       "180", "g"),
                r(p+"_MI_11", restaurantId, "Butter",          "20", "g"),
                // Hyderabadi Biryani
                r(p+"_MI_12", restaurantId, "Basmati Rice",  "250", "g"),
                r(p+"_MI_12", restaurantId, "Mutton",        "200", "g"),
                r(p+"_MI_12", restaurantId, "Saffron",         "1", "g"),
                r(p+"_MI_12", restaurantId, "Onions",         "80", "g"),
                r(p+"_MI_12", restaurantId, "Yogurt",         "40", "g"),
                // Chole Bhature
                r(p+"_MI_13", restaurantId, "Wheat Flour",   "200", "g"),
                r(p+"_MI_13", restaurantId, "Cumin Seeds",     "3", "g"),
                // Fish Amritsari
                r(p+"_MI_14", restaurantId, "Fish",          "250", "g"),
                r(p+"_MI_14", restaurantId, "Wheat Flour",    "80", "g"),
                r(p+"_MI_14", restaurantId, "Garlic",         "10", "g"),
                // Veg Biryani
                r(p+"_MI_15", restaurantId, "Basmati Rice",  "200", "g"),
                r(p+"_MI_15", restaurantId, "Onions",         "60", "g"),
                // Butter Naan
                r(p+"_MI_16", restaurantId, "Wheat Flour",    "70", "g"),
                r(p+"_MI_16", restaurantId, "Butter",          "20", "g"),
                // Cheese Garlic Naan
                r(p+"_MI_17", restaurantId, "Wheat Flour",    "80", "g"),
                r(p+"_MI_17", restaurantId, "Garlic",         "10", "g"),
                // Laccha Paratha
                r(p+"_MI_18", restaurantId, "Wheat Flour",    "90", "g"),
                r(p+"_MI_18", restaurantId, "Butter",          "15", "g"),
                // Missi Roti
                r(p+"_MI_19", restaurantId, "Wheat Flour",    "80", "g"),
                r(p+"_MI_19", restaurantId, "Cumin Seeds",     "2", "g"),
                // Rasmalai
                r(p+"_MI_20", restaurantId, "Paneer",        "120", "g"),
                r(p+"_MI_20", restaurantId, "Milk",          "200", "ml"),
                r(p+"_MI_20", restaurantId, "Saffron",         "1", "g"),
                // Kulfi Falooda
                r(p+"_MI_21", restaurantId, "Milk",          "250", "ml"),
                r(p+"_MI_21", restaurantId, "Sugar",          "30", "g"),
                r(p+"_MI_21", restaurantId, "Cashews",        "10", "g"),
                // Gajar Ka Halwa
                r(p+"_MI_22", restaurantId, "Milk",          "300", "ml"),
                r(p+"_MI_22", restaurantId, "Sugar",          "40", "g"),
                r(p+"_MI_22", restaurantId, "Almonds",        "15", "g"),
                // Phirni
                r(p+"_MI_23", restaurantId, "Milk",          "200", "ml"),
                r(p+"_MI_23", restaurantId, "Sugar",          "30", "g"),
                // Mango Lassi
                r(p+"_MI_24", restaurantId, "Yogurt",        "200", "g"),
                r(p+"_MI_24", restaurantId, "Sugar",          "20", "g"),
                // Cold Coffee
                r(p+"_MI_25", restaurantId, "Milk",          "200", "ml"),
                r(p+"_MI_25", restaurantId, "Sugar",          "25", "g")
        );
        menuItemIngredientRepository.saveAll(recipes);
    }

    private MenuItemIngredient r(String miId, String restId, String name, String qty, String unit) {
        return MenuItemIngredient.builder().menuItemId(miId).restaurantId(restId)
                .name(name).quantityPerUnit(new BigDecimal(qty)).unit(unit).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ORDERS V2 — 30 orders per restaurant, all statuses, all order types
    // ═══════════════════════════════════════════════════════════════════════

    private void seedSampleOrdersV2(String restaurantId, String p) {
        // Also seed recipes for this restaurant
        seedRecipesV2(restaurantId, p);

        int n = 1; // order sequence

        // ── NEW orders (just placed, kitchen hasn't started) ──
        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "12:30", "Demo Customer", "+919999000002", "customer@savorystay.com", "USR_CUSTOMER",
                "PAID", "UPI", "NEW", -3,
                new String[][]{{p+"_MI_1","Butter Chicken","420","2"},{p+"_MI_3","Garlic Naan","80","2"}});

        placeOrder(p, restaurantId, n++, "DINE_IN", 4, 2, "13:00", "Priya Sharma", "+919876543210", "priya@example.com", "USR_CUST_2",
                "PAID", "CARD", "NEW", -5,
                new String[][]{{p+"_MI_2","Paneer Tikka","320","1"},{p+"_MI_11","Palak Paneer","240","1"},{p+"_MI_16","Butter Naan","70","3"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "19:30", "Rahul Mehta", "+919812345678", "rahul.m@example.com", "USR_CUST_3",
                "PENDING", "CASH", "NEW", -2,
                new String[][]{{p+"_MI_10","Mutton Rogan Josh","480","1"},{p+"_MI_5","Masala Chai","60","1"},{p+"_MI_16","Butter Naan","70","2"}});

        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "19:00", "Ananya Verma", "+919765432109", "ananya@example.com", "USR_CUST_4",
                "PAID", "UPI", "NEW", -1,
                new String[][]{{p+"_MI_8","Tandoori Prawns","450","1"},{p+"_MI_5","Masala Chai","60","1"},{p+"_MI_16","Butter Naan","70","2"}});

        placeOrder(p, restaurantId, n++, "DINE_IN", 7, 4, "20:00", "Neha Gupta", "+919543210987", "neha@example.com", "USR_CUST_6",
                "PAID", "UPI", "NEW", -4,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","2"},{p+"_MI_7","Crispy Paneer 65","260","1"},{p+"_MI_21","Kulfi Falooda","160","2"}});

        // ── PREPARING orders (kitchen is actively cooking) ──
        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "20:00", "Vikram Patel", "+919654321098", "vikram@example.com", "USR_CUST_5",
                "PAID", "UPI", "PREPARING", -35,
                new String[][]{{p+"_MI_1","Butter Chicken","420","1"},{p+"_MI_11","Palak Paneer","240","1"},{p+"_MI_20","Rasmalai","180","1"}});

        placeOrder(p, restaurantId, n++, "DINE_IN", 3, 2, "14:00", "Arjun Singh", "+919432109876", "arjun@example.com", "USR_CUST_7",
                "PAID", "CARD", "PREPARING", -40,
                new String[][]{{p+"_MI_6","Chicken Seekh Kebab","280","2"},{p+"_MI_1","Butter Chicken","420","1"},{p+"_MI_17","Cheese Garlic Naan","120","2"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "19:15", "Meera Iyer", "+919321098765", "meera@example.com", "USR_CUST_8",
                "PAID", "UPI", "PREPARING", -28,
                new String[][]{{p+"_MI_14","Fish Amritsari","380","1"},{p+"_MI_18","Laccha Paratha","90","2"},{p+"_MI_24","Mango Lassi","120","1"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "19:45", "Demo Customer", "+919999000002", "customer@savorystay.com", "USR_CUSTOMER",
                "PAID", "MOCK", "PREPARING", -45,
                new String[][]{{p+"_MI_9","Dal Makhani","260","2"},{p+"_MI_19","Missi Roti","75","3"},{p+"_MI_4","Gulab Jamun","150","2"}});

        // ── PACKED_READY orders (ready for pickup/handover) ──
        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "18:45", "Demo Customer", "+919999000002", "customer@savorystay.com", "USR_CUSTOMER",
                "PAID", "MOCK", "PACKED_READY", -120,
                new String[][]{{p+"_MI_9","Dal Makhani","260","1"},{p+"_MI_17","Cheese Garlic Naan","120","2"}});

        placeOrder(p, restaurantId, n++, "DINE_IN", 12, 6, "19:00", "Rahul Mehta", "+919812345678", "rahul.m@example.com", "USR_CUST_3",
                "PENDING", "CASH", "PACKED_READY", -60,
                new String[][]{{p+"_MI_1","Butter Chicken","420","3"},{p+"_MI_10","Mutton Rogan Josh","480","1"},{p+"_MI_3","Garlic Naan","80","3"},{p+"_MI_21","Kulfi Falooda","160","2"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "19:15", "Vikram Patel", "+919654321098", "vikram@example.com", "USR_CUST_5",
                "PAID", "UPI", "PACKED_READY", -50,
                new String[][]{{p+"_MI_2","Paneer Tikka","320","1"},{p+"_MI_20","Rasmalai","180","1"}});

        // ── COMPLETED orders (delivered/handed over) ──
        placeOrder(p, restaurantId, n++, "DINE_IN", 3, 2, "13:00", "Rahul Mehta", "+919812345678", "rahul.m@example.com", "USR_CUST_3",
                "PAID", "UPI", "COMPLETED", -300,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","1"},{p+"_MI_16","Butter Naan","70","2"},{p+"_MI_24","Mango Lassi","120","1"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "11:00", "Priya Sharma", "+919876543210", "priya@example.com", "USR_CUST_2",
                "PAID", "CARD", "COMPLETED", -360,
                new String[][]{{p+"_MI_13","Chole Bhature","180","1"},{p+"_MI_5","Masala Chai","60","2"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "12:00", "Ananya Verma", "+919765432109", "ananya@example.com", "USR_CUST_4",
                "PAID", "UPI", "COMPLETED", -420,
                new String[][]{{p+"_MI_1","Butter Chicken","420","1"},{p+"_MI_18","Laccha Paratha","90","2"},{p+"_MI_22","Gajar Ka Halwa","140","1"}});

        placeOrder(p, restaurantId, n++, "DINE_IN", 5, 3, "14:30", "Neha Gupta", "+919543210987", "neha@example.com", "USR_CUST_6",
                "PAID", "UPI", "COMPLETED", -380,
                new String[][]{{p+"_MI_14","Fish Amritsari","380","2"},{p+"_MI_9","Dal Makhani","260","1"},{p+"_MI_16","Butter Naan","70","3"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "13:30", "Vikram Patel", "+919654321098", "vikram@example.com", "USR_CUST_5",
                "PAID", "MOCK", "COMPLETED", -340,
                new String[][]{{p+"_MI_7","Crispy Paneer 65","260","1"},{p+"_MI_15","Veg Biryani","300","1"},{p+"_MI_23","Phirni","130","1"}});

        placeOrder(p, restaurantId, n++, "DINE_IN", 2, 2, "12:30", "Arjun Singh", "+919432109876", "arjun@example.com", "USR_CUST_7",
                "PAID", "CARD", "COMPLETED", -290,
                new String[][]{{p+"_MI_6","Chicken Seekh Kebab","280","1"},{p+"_MI_11","Palak Paneer","240","1"},{p+"_MI_17","Cheese Garlic Naan","120","1"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "14:00", "Meera Iyer", "+919321098765", "meera@example.com", "USR_CUST_8",
                "PAID", "UPI", "COMPLETED", -330,
                new String[][]{{p+"_MI_1","Butter Chicken","420","1"},{p+"_MI_4","Gulab Jamun","150","2"},{p+"_MI_5","Masala Chai","60","2"}});

        placeOrder(p, restaurantId, n++, "DINE_IN", 4, 3, "13:00", "Demo Customer", "+919999000002", "customer@savorystay.com", "USR_CUSTOMER",
                "PAID", "UPI", "COMPLETED", -350,
                new String[][]{{p+"_MI_10","Mutton Rogan Josh","480","2"},{p+"_MI_3","Garlic Naan","80","4"},{p+"_MI_20","Rasmalai","180","2"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "15:00", "Priya Sharma", "+919876543210", "priya@example.com", "USR_CUST_2",
                "PAID", "MOCK", "COMPLETED", -280,
                new String[][]{{p+"_MI_13","Chole Bhature","180","2"},{p+"_MI_24","Mango Lassi","120","2"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "11:30", "Ananya Verma", "+919765432109", "ananya@example.com", "USR_CUST_4",
                "PAID", "UPI", "COMPLETED", -370,
                new String[][]{{p+"_MI_15","Veg Biryani","300","1"},{p+"_MI_16","Butter Naan","70","2"},{p+"_MI_21","Kulfi Falooda","160","1"}});

        // ── DECLINED orders (kitchen at capacity) ──
        placeOrder(p, restaurantId, n++, "DINE_IN", 6, 4, "14:00", "Walk-in Guest", "+919000000000", null, null,
                "PENDING", "CASH", "DECLINED", -240,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","3"},{p+"_MI_1","Butter Chicken","420","2"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "13:00", "Arjun Singh", "+919432109876", "arjun@example.com", "USR_CUST_7",
                "PENDING", "CASH", "DECLINED", -200,
                new String[][]{{p+"_MI_10","Mutton Rogan Josh","480","2"}});

        // ── CANCELLED orders ──
        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "17:00", "Ananya Verma", "+919765432109", "ananya@example.com", "USR_CUST_4",
                "PENDING", "UPI", "CANCELLED", -180,
                new String[][]{{p+"_MI_2","Paneer Tikka","320","1"}});

        placeOrder(p, restaurantId, n++, "PICKUP", null, null, "18:00", "Vikram Patel", "+919654321098", "vikram@example.com", "USR_CUST_5",
                "PENDING", "MOCK", "CANCELLED", -150,
                new String[][]{{p+"_MI_14","Fish Amritsari","380","1"},{p+"_MI_19","Missi Roti","75","2"}});

        // ══════════════════════════════════════════════════════════════
        //  EXTRA DINE-IN ORDERS — various tables, times, statuses
        // ══════════════════════════════════════════════════════════════

        // DINE_IN NEW — 2-seater lunch
        placeOrder(p, restaurantId, n++, "DINE_IN", 2, 2, "12:30", "Neha Gupta", "+919543210987", "neha@example.com", "USR_CUST_6",
                "PAID", "UPI", "NEW", -6,
                new String[][]{{p+"_MI_11","Palak Paneer","240","1"},{p+"_MI_16","Butter Naan","70","2"},{p+"_MI_24","Mango Lassi","120","1"}});

        // DINE_IN NEW — 6-seater dinner party
        placeOrder(p, restaurantId, n++, "DINE_IN", 6, 6, "20:00", "Meera Iyer", "+919321098765", "meera@example.com", "USR_CUST_8",
                "PAID", "CARD", "NEW", -8,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","3"},{p+"_MI_6","Chicken Seekh Kebab","280","2"},{p+"_MI_7","Crispy Paneer 65","260","1"},{p+"_MI_21","Kulfi Falooda","160","3"}});

        // DINE_IN PREPARING — 4-seater late lunch
        placeOrder(p, restaurantId, n++, "DINE_IN", 4, 3, "13:30", "Vikram Patel", "+919654321098", "vikram@example.com", "USR_CUST_5",
                "PAID", "UPI", "PREPARING", -30,
                new String[][]{{p+"_MI_10","Mutton Rogan Josh","480","2"},{p+"_MI_18","Laccha Paratha","90","3"},{p+"_MI_9","Dal Makhani","260","1"}});

        // DINE_IN PREPARING — 2-seater couple dinner
        placeOrder(p, restaurantId, n++, "DINE_IN", 2, 2, "19:30", "Ananya Verma", "+919765432109", "ananya@example.com", "USR_CUST_4",
                "PAID", "UPI", "PREPARING", -32,
                new String[][]{{p+"_MI_8","Tandoori Prawns","450","2"},{p+"_MI_19","Missi Roti","75","2"},{p+"_MI_22","Gajar Ka Halwa","140","2"}});

        // DINE_IN PREPARING — 4-seater family dinner
        placeOrder(p, restaurantId, n++, "DINE_IN", 4, 4, "19:00", "Priya Sharma", "+919876543210", "priya@example.com", "USR_CUST_2",
                "PAID", "CARD", "PREPARING", -38,
                new String[][]{{p+"_MI_1","Butter Chicken","420","2"},{p+"_MI_14","Fish Amritsari","380","1"},{p+"_MI_3","Garlic Naan","80","4"},{p+"_MI_20","Rasmalai","180","2"}});

        // DINE_IN PACKED_READY — 6-seater corporate dinner
        placeOrder(p, restaurantId, n++, "DINE_IN", 6, 6, "20:30", "Demo Customer", "+919999000002", "customer@savorystay.com", "USR_CUSTOMER",
                "PAID", "CARD", "PACKED_READY", -55,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","4"},{p+"_MI_1","Butter Chicken","420","2"},{p+"_MI_2","Paneer Tikka","320","2"},{p+"_MI_3","Garlic Naan","80","6"},{p+"_MI_21","Kulfi Falooda","160","4"}});

        // DINE_IN PACKED_READY — 2-seater quick dinner
        placeOrder(p, restaurantId, n++, "DINE_IN", 2, 2, "19:00", "Arjun Singh", "+919432109876", "arjun@example.com", "USR_CUST_7",
                "PAID", "UPI", "PACKED_READY", -45,
                new String[][]{{p+"_MI_15","Veg Biryani","300","1"},{p+"_MI_7","Crispy Paneer 65","260","1"},{p+"_MI_24","Mango Lassi","120","1"}});

        // DINE_IN COMPLETED — 4-seater lunch
        placeOrder(p, restaurantId, n++, "DINE_IN", 4, 3, "13:00", "Vikram Patel", "+919654321098", "vikram@example.com", "USR_CUST_5",
                "PAID", "UPI", "COMPLETED", -310,
                new String[][]{{p+"_MI_1","Butter Chicken","420","2"},{p+"_MI_9","Dal Makhani","260","1"},{p+"_MI_16","Butter Naan","70","3"}});

        // DINE_IN COMPLETED — 6-seater family celebration
        placeOrder(p, restaurantId, n++, "DINE_IN", 6, 6, "14:00", "Neha Gupta", "+919543210987", "neha@example.com", "USR_CUST_6",
                "PAID", "CARD", "COMPLETED", -270,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","3"},{p+"_MI_10","Mutton Rogan Josh","480","2"},{p+"_MI_7","Crispy Paneer 65","260","2"},{p+"_MI_17","Cheese Garlic Naan","120","4"},{p+"_MI_4","Gulab Jamun","150","4"}});

        // DINE_IN COMPLETED — 2-seater dinner
        placeOrder(p, restaurantId, n++, "DINE_IN", 2, 2, "19:30", "Meera Iyer", "+919321098765", "meera@example.com", "USR_CUST_8",
                "PAID", "UPI", "COMPLETED", -260,
                new String[][]{{p+"_MI_8","Tandoori Prawns","450","1"},{p+"_MI_11","Palak Paneer","240","1"},{p+"_MI_20","Rasmalai","180","1"}});

        // DINE_IN DECLINED — 6-seater too large for capacity
        placeOrder(p, restaurantId, n++, "DINE_IN", 6, 6, "13:00", "Walk-in Guest", "+919000000000", null, null,
                "PENDING", "CASH", "DECLINED", -230,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","4"},{p+"_MI_1","Butter Chicken","420","2"},{p+"_MI_3","Garlic Naan","80","6"}});

        // ══════════════════════════════════════════════════════════════
        //  EXTRA PRE-ORDER ORDERS — various future dates & time slots
        // ══════════════════════════════════════════════════════════════

        // PRE_ORDER NEW — tomorrow lunch
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "12:00", "Priya Sharma", "+919876543210", "priya@example.com", "USR_CUST_2",
                "PAID", "UPI", "NEW", -2,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","2"},{p+"_MI_7","Crispy Paneer 65","260","1"},{p+"_MI_24","Mango Lassi","120","2"}});

        // PRE_ORDER NEW — tomorrow dinner
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "19:00", "Arjun Singh", "+919432109876", "arjun@example.com", "USR_CUST_7",
                "PAID", "CARD", "NEW", -3,
                new String[][]{{p+"_MI_10","Mutton Rogan Josh","480","2"},{p+"_MI_18","Laccha Paratha","90","4"},{p+"_MI_4","Gulab Jamun","150","2"}});

        // PRE_ORDER NEW — day after tomorrow lunch
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "12:30", "Demo Customer", "+919999000002", "customer@savorystay.com", "USR_CUSTOMER",
                "PAID", "UPI", "NEW", -4,
                new String[][]{{p+"_MI_14","Fish Amritsari","380","2"},{p+"_MI_19","Missi Roti","75","3"},{p+"_MI_21","Kulfi Falooda","160","2"}});

        // PRE_ORDER PREPARING — kitchen started prepping tomorrow's orders
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "13:00", "Neha Gupta", "+919543210987", "neha@example.com", "USR_CUST_6",
                "PAID", "UPI", "PREPARING", -10,
                new String[][]{{p+"_MI_1","Butter Chicken","420","3"},{p+"_MI_9","Dal Makhani","260","2"},{p+"_MI_16","Butter Naan","70","4"},{p+"_MI_23","Phirni","130","3"}});

        // PRE_ORDER PREPARING — evening pre-order in prep
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "19:30", "Meera Iyer", "+919321098765", "meera@example.com", "USR_CUST_8",
                "PAID", "CARD", "PREPARING", -12,
                new String[][]{{p+"_MI_8","Tandoori Prawns","450","2"},{p+"_MI_11","Palak Paneer","240","2"},{p+"_MI_17","Cheese Garlic Naan","120","3"}});

        // PRE_ORDER PACKED_READY — ready for tomorrow pickup
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "12:00", "Ananya Verma", "+919765432109", "ananya@example.com", "USR_CUST_4",
                "PAID", "UPI", "PACKED_READY", -15,
                new String[][]{{p+"_MI_12","Hyderabadi Biryani","520","1"},{p+"_MI_2","Paneer Tikka","320","2"},{p+"_MI_20","Rasmalai","180","2"}});

        // PRE_ORDER COMPLETED — past pre-order delivered
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "12:00", "Vikram Patel", "+919654321098", "vikram@example.com", "USR_CUST_5",
                "PAID", "MOCK", "COMPLETED", -1440,
                new String[][]{{p+"_MI_10","Mutton Rogan Josh","480","2"},{p+"_MI_3","Garlic Naan","80","4"},{p+"_MI_22","Gajar Ka Halwa","140","2"}});

        // PRE_ORDER COMPLETED — another past pre-order
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "19:00", "Priya Sharma", "+919876543210", "priya@example.com", "USR_CUST_2",
                "PAID", "UPI", "COMPLETED", -1500,
                new String[][]{{p+"_MI_1","Butter Chicken","420","2"},{p+"_MI_11","Palak Paneer","240","1"},{p+"_MI_19","Missi Roti","75","3"},{p+"_MI_21","Kulfi Falooda","160","2"}});

        // PRE_ORDER CANCELLED — customer cancelled pre-order
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "13:00", "Arjun Singh", "+919432109876", "arjun@example.com", "USR_CUST_7",
                "PENDING", "UPI", "CANCELLED", -20,
                new String[][]{{p+"_MI_14","Fish Amritsari","380","1"},{p+"_MI_18","Laccha Paratha","90","2"}});

        // PRE_ORDER CANCELLED — another cancelled pre-order
        placeOrder(p, restaurantId, n++, "PRE_ORDER", null, null, "12:00", "Neha Gupta", "+919543210987", "neha@example.com", "USR_CUST_6",
                "PENDING", "MOCK", "CANCELLED", -25,
                new String[][]{{p+"_MI_6","Chicken Seekh Kebab","280","2"},{p+"_MI_15","Veg Biryani","300","1"},{p+"_MI_4","Gulab Jamun","150","2"}});

        log.info("DataSeeder: {} orders seeded for {}", n - 1, restaurantId);
    }

    /**
     * Helper to place an order with items in one call.
     * @param minutesAgo negative offset from now for createdAt
     */
    private void placeOrder(String p, String restaurantId, int seq, String orderType,
                            Integer tableNumber, Integer guests, String timeSlot,
                            String custName, String phone, String email, String userId,
                            String payStatus, String payMethod, String orderStatus, int minutesAgo,
                            String[][] items) {
        String orderId = p + "_ORD_" + seq;
        String orderNum = "#ORD-" + p.charAt(p.length()-1) + String.format("%03d", seq);

        BigDecimal total = BigDecimal.ZERO;
        for (String[] item : items) {
            total = total.add(new BigDecimal(item[2]).multiply(BigDecimal.valueOf(Integer.parseInt(item[3]))));
        }

        Order order = Order.builder()
                .id(orderId).orderNumber(orderNum).restaurantId(restaurantId)
                .orderType(orderType).tableNumber(tableNumber).guests(guests)
                .timeSlot(timeSlot).pickupTime(timeSlot)
                .customerName(custName).customerPhone(phone).customerEmail(email).userId(userId)
                .totalAmount(total).paymentStatus(payStatus).paymentMethod(payMethod)
                .orderStatus(orderStatus).createdAt(LocalDateTime.now().plusMinutes(minutesAgo))
                .build();
        if ("DECLINED".equals(orderStatus)) {
            order.setCancelReason("Kitchen at full capacity");
            order.setCancelledBy("USR_R1_MGR");
        } else if ("CANCELLED".equals(orderStatus)) {
            order.setCancelReason("Customer changed mind");
            order.setCancelledBy(userId);
        }
        orderRepository.save(order);

        for (int i = 0; i < items.length; i++) {
            orderItemRepository.save(OrderItem.builder()
                    .id(orderId + "_OI_" + (i+1)).orderId(orderId).menuItemId(items[i][0])
                    .title(items[i][1]).quantity(Integer.parseInt(items[i][3]))
                    .unitPrice(new BigDecimal(items[i][2])).build());
        }
    }

    // ─── PRICE RULES ─────────────────────────────────────────────────────

    private void seedPriceRulesV2(String restaurantId, String p) {
        // Happy hour discount on some starters (starts yesterday, so active now)
        priceRuleRepository.save(PriceRule.builder().menuItemId(p+"_MI_2").price(new BigDecimal("280"))
                .effectiveFrom(LocalDateTime.now().minusDays(1)).build());
        priceRuleRepository.save(PriceRule.builder().menuItemId(p+"_MI_7").price(new BigDecimal("220"))
                .effectiveFrom(LocalDateTime.now().minusDays(1)).build());
        // Weekend premium on biryani (starts in 7 days — shows as upcoming)
        priceRuleRepository.save(PriceRule.builder().menuItemId(p+"_MI_12").price(new BigDecimal("580"))
                .effectiveFrom(LocalDateTime.now().plusDays(7)).build());
        // Cold coffee summer discount (starts in 14 days)
        priceRuleRepository.save(PriceRule.builder().menuItemId(p+"_MI_25").price(new BigDecimal("120"))
                .effectiveFrom(LocalDateTime.now().plusDays(14)).build());
    }

    // ─── RESTAURANT SETTINGS ─────────────────────────────────────────────

    private void seedRestaurantSettings() {
        restaurantSettingsRepository.save(RestaurantSettings.builder()
                .restaurantId("REST_DEMO_1")
                .tableConfig("[{\"type\":\"2-Seater\",\"count\":6},{\"type\":\"4-Seater\",\"count\":5},{\"type\":\"6-Seater\",\"count\":3}]")
                .totalTables(14)
                .pickupTimeSlots("15 Mins,30 Mins,45 Mins,1 Hour,1.5 Hours")
                .dineinTimeSlots("12:00 PM,12:30 PM,1:00 PM,1:30 PM,2:00 PM,7:00 PM,7:30 PM,8:00 PM,8:30 PM,9:00 PM,9:30 PM")
                .build());
        restaurantSettingsRepository.save(RestaurantSettings.builder()
                .restaurantId("REST_DEMO_2")
                .tableConfig("[{\"type\":\"2-Seater\",\"count\":4},{\"type\":\"4-Seater\",\"count\":3},{\"type\":\"6-Seater\",\"count\":2}]")
                .totalTables(9)
                .pickupTimeSlots("15 Mins,30 Mins,45 Mins,1 Hour")
                .dineinTimeSlots("12:00 PM,12:30 PM,1:00 PM,7:00 PM,7:30 PM,8:00 PM,8:30 PM,9:00 PM")
                .build());
    }

    // ─── MEMBERSHIPS ─────────────────────────────────────────────────────

    private void seedMemberships() {
        List<String> customers = List.of("USR_CUSTOMER","USR_CUST_2","USR_CUST_3","USR_CUST_4","USR_CUST_5","USR_CUST_6","USR_CUST_7","USR_CUST_8");
        List<String> restaurants = List.of("REST_DEMO_1","REST_DEMO_2");
        for (String custId : customers) {
            for (String restId : restaurants) {
                customerRestaurantRepository.save(CustomerRestaurant.builder()
                        .customerId(custId).restaurantId(restId)
                        .joinedAt(LocalDateTime.now().minusDays((long)(Math.random() * 30))).build());
            }
        }
    }

    // ─── NOTIFICATIONS ───────────────────────────────────────────────────

    private void seedNotifications() {
        notificationRepository.save(Notification.builder().userId("USR_CUSTOMER").restaurantId("REST_DEMO_1").orderId("REST_DEMO_1_ORD_1")
                .title("Order Confirmed").message("Your order #ORD-1001 has been placed successfully!").type("ORDER_STATUS").channel("APP").read(false).createdAt(LocalDateTime.now().minusMinutes(3)).build());
        notificationRepository.save(Notification.builder().userId("USR_CUSTOMER").restaurantId("REST_DEMO_1").orderId("REST_DEMO_1_ORD_7")
                .title("Order Ready!").message("Your order #ORD-1007 is packed and ready for pickup.").type("ORDER_READY").channel("APP,SMS").read(false).createdAt(LocalDateTime.now().minusMinutes(30)).build());
        notificationRepository.save(Notification.builder().userId("USR_CUST_2").restaurantId("REST_DEMO_1").orderId("REST_DEMO_1_ORD_2")
                .title("Order Confirmed").message("Your order #ORD-1002 has been placed. Preparing now!").type("ORDER_STATUS").channel("APP").read(false).createdAt(LocalDateTime.now().minusMinutes(5)).build());
        notificationRepository.save(Notification.builder().userId("USR_CUST_5").restaurantId("REST_DEMO_1").orderId("REST_DEMO_1_ORD_6")
                .title("Preparing").message("Chef is cooking your order #ORD-1006. Hang tight!").type("ORDER_STATUS").channel("APP").read(true).createdAt(LocalDateTime.now().minusMinutes(20)).build());
        notificationRepository.save(Notification.builder().userId("USR_R1_CHEF").restaurantId("REST_DEMO_1").orderId("REST_DEMO_1_ORD_1")
                .title("New Order").message("New order #ORD-1001 received. 2x Butter Chicken, 2x Garlic Naan.").type("NEW_ORDER").channel("APP").read(false).createdAt(LocalDateTime.now().minusMinutes(3)).build());
        notificationRepository.save(Notification.builder().userId("USR_R1_MGR").restaurantId("REST_DEMO_1")
                .title("Cash Payment Pending").message("Order #ORD-1005 has a pending CASH payment of ₹1,850.").type("STAFF").channel("APP").read(false).createdAt(LocalDateTime.now().minusMinutes(2)).build());
        notificationRepository.save(Notification.builder().userId("USR_R1_ADMIN").restaurantId("REST_DEMO_1")
                .title("Stock Alert").message("Saffron is below reorder level. Current: 40g, Reorder at: 100g.").type("SYSTEM").channel("APP").read(false).createdAt(LocalDateTime.now().minusMinutes(15)).build());
        notificationRepository.save(Notification.builder().userId("USR_R1_ADMIN").restaurantId("REST_DEMO_1")
                .title("Stock Alert").message("Cardamom is below reorder level. Current: 60g, Reorder at: 150g.").type("SYSTEM").channel("APP").read(false).createdAt(LocalDateTime.now().minusMinutes(15)).build());
    }
}
