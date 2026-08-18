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
        // users.email became nullable so staff without an email address can be
        // created with a phone number only (MySQL allows repeated NULLs in a
        // unique index). Existing databases still have NOT NULL on the column.
        makeEmailNullable();
    }

    private void makeEmailNullable() {
        try {
            String nullable = jdbcTemplate.query(
                    "SELECT IS_NULLABLE FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email'",
                    rs -> rs.next() ? rs.getString(1) : null);
            if ("NO".equalsIgnoreCase(nullable)) {
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN email VARCHAR(100) NULL");
                log.info("DatabaseSchemaMigration: users.email made nullable (staff can now be created without an email).");
            }
        } catch (Exception e) {
            // Fresh databases already match the entity; a failure here is non-fatal.
            log.warn("DatabaseSchemaMigration: could not verify users.email nullability: {}", e.getMessage());
        }
    }
}
