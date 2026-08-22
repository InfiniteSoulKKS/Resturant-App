package com.savorystay.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off, idempotent schema migrations for databases that pre-date entity
 * changes. Hibernate's {@code ddl-auto: update} is unreliable at dropping
 * NOT NULL from an existing column, so this checks the live column metadata
 * and alters only when needed — it runs before DataSeeder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class DatabaseSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        makeEmailNullable();
        addDailyPlateCountColumn();
        addPlateCapacityTable();
        addTableSlotCapacityTable();
        addOrderIndexes();
        addOrderItemIndexes();
        addIngredientSnapshotColumn();
        addOutboxLockColumns();
        addCustomerRestaurantIndex();
        addPaymentIndex();
    }

    private void makeEmailNullable() {
        try {
            String nullable = jdbcTemplate.query(
                    "SELECT IS_NULLABLE FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email'",
                    rs -> rs.next() ? rs.getString(1) : null);
            if ("NO".equalsIgnoreCase(nullable)) {
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN email VARCHAR(100) NULL");
                log.info("DatabaseSchemaMigration: users.email made nullable.");
            }
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: could not verify users.email nullability: {}", e.getMessage());
        }
    }

    private void addDailyPlateCountColumn() {
        try {
            if (!columnExists("menu_items", "daily_plate_count")) {
                jdbcTemplate.execute("ALTER TABLE menu_items ADD COLUMN daily_plate_count INT NULL AFTER prep_minutes");
                log.info("DatabaseSchemaMigration: added menu_items.daily_plate_count column.");
            }
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: daily_plate_count migration skipped: {}", e.getMessage());
        }
    }

    private void addPlateCapacityTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plate_capacity (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    menu_item_id VARCHAR(64) NOT NULL,
                    restaurant_id VARCHAR(64) NOT NULL,
                    business_date DATE NOT NULL,
                    capacity INT NOT NULL DEFAULT 0,
                    reserved_count INT NOT NULL DEFAULT 0,
                    version BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP,
                    UNIQUE KEY uk_plate_capacity (menu_item_id, business_date),
                    INDEX idx_plate_capacity_restaurant_date (restaurant_id, business_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("DatabaseSchemaMigration: plate_capacity table ensured.");
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: plate_capacity table migration skipped: {}", e.getMessage());
        }
    }

    private void addTableSlotCapacityTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS table_slot_capacity (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    restaurant_id VARCHAR(64) NOT NULL,
                    business_date DATE NOT NULL,
                    time_slot VARCHAR(100) NOT NULL,
                    table_type VARCHAR(30) NOT NULL,
                    total_capacity INT NOT NULL DEFAULT 0,
                    reserved_count INT NOT NULL DEFAULT 0,
                    version BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP,
                    UNIQUE KEY uk_table_slot_capacity (restaurant_id, business_date, time_slot, table_type),
                    INDEX idx_table_slot_restaurant_date (restaurant_id, business_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("DatabaseSchemaMigration: table_slot_capacity table ensured.");
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: table_slot_capacity table migration skipped: {}", e.getMessage());
        }
    }

    private void addOrderIndexes() {
        try {
            addIndexIfMissing("idx_orders_user_created",
                    "CREATE INDEX idx_orders_user_created ON orders (user_id, created_at)");
            addIndexIfMissing("idx_orders_restaurant_status",
                    "CREATE INDEX idx_orders_restaurant_status ON orders (restaurant_id, order_status)");
            addIndexIfMissing("idx_orders_restaurant_created",
                    "CREATE INDEX idx_orders_restaurant_created ON orders (restaurant_id, created_at)");
            addIndexIfMissing("idx_orders_restaurant_pickup",
                    "CREATE INDEX idx_orders_restaurant_pickup ON orders (restaurant_id, pickup_time)");
            log.info("DatabaseSchemaMigration: order indexes ensured.");
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: order index migration skipped: {}", e.getMessage());
        }
    }

    private void addOrderItemIndexes() {
        try {
            addIndexIfMissing("idx_order_items_menu_created",
                    "CREATE INDEX idx_order_items_menu_created ON order_items (menu_item_id, created_at)");
            log.info("DatabaseSchemaMigration: order_items indexes ensured.");
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: order_items index migration skipped: {}", e.getMessage());
        }
    }

    /** P0.13: Add ingredient_snapshot TEXT column to order_items for recipe preservation. */
    private void addIngredientSnapshotColumn() {
        try {
            if (!columnExists("order_items", "ingredient_snapshot")) {
                jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN ingredient_snapshot TEXT NULL AFTER unit_price");
                log.info("DatabaseSchemaMigration: order_items.ingredient_snapshot column added.");
            }
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: ingredient_snapshot migration skipped: {}", e.getMessage());
        }
    }

    private void addOutboxLockColumns() {
        try {
            if (!columnExists("outbox_event", "locked_at")) {
                jdbcTemplate.execute("ALTER TABLE outbox_event ADD COLUMN locked_at TIMESTAMP NULL AFTER status");
                jdbcTemplate.execute("ALTER TABLE outbox_event ADD COLUMN locked_by VARCHAR(100) NULL AFTER locked_at");
                log.info("DatabaseSchemaMigration: outbox_event lock columns added.");
            }
            // Drop old index and create new composite one
            try {
                jdbcTemplate.execute("DROP INDEX idx_outbox_unpublished ON outbox_event");
            } catch (Exception ignored) { }
            addIndexIfMissing("idx_outbox_status_next",
                    "CREATE INDEX idx_outbox_status_next ON outbox_event (status, locked_at)");
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: outbox lock migration skipped: {}", e.getMessage());
        }
    }

    private void addCustomerRestaurantIndex() {
        try {
            addIndexIfMissing("idx_customer_restaurant_customer",
                    "CREATE INDEX idx_customer_restaurant_customer ON customer_restaurant (customer_id, restaurant_id)");
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: customer_restaurant index skipped: {}", e.getMessage());
        }
    }

    private void addPaymentIndex() {
        try {
            addIndexIfMissing("idx_payments_order",
                    "CREATE INDEX idx_payments_order ON payments (order_id)");
        } catch (Exception e) {
            log.warn("DatabaseSchemaMigration: payments index skipped: {}", e.getMessage());
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────

    private boolean columnExists(String table, String column) {
        try {
            String exists = jdbcTemplate.query(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = '" + table +
                            "' AND column_name = '" + column + "'",
                    rs -> rs.next() ? rs.getString(1) : "0");
            return "1".equals(exists);
        } catch (Exception e) {
            return false;
        }
    }

    private void addIndexIfMissing(String indexName, String createSql) {
        try {
            Boolean exists = jdbcTemplate.query(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() AND index_name = '" + indexName + "'",
                    rs -> rs.next() && rs.getInt(1) > 0);
            if (!Boolean.TRUE.equals(exists)) {
                jdbcTemplate.execute(createSql);
            }
        } catch (Exception e) {
            // Index may already exist or table may not exist yet
            log.debug("Index {} creation skipped: {}", indexName, e.getMessage());
        }
    }
}
