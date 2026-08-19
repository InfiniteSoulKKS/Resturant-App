package com.savorystay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class SavoryStayApplication {

    public static void main(String[] args) {
        // Load .env file FIRST, before Spring starts, so required secrets are
        // available to both validateRequiredSecrets() and application.yml placeholders.
        loadDotEnv();

        // Fail fast BEFORE Spring / Hibernate start, so a missing or weak secret
        // surfaces as a clear, actionable message instead of a runtime 500 or a
        // noisy DB connection error.
        validateRequiredSecrets();

        SpringApplication.run(SavoryStayApplication.class, args);
        log.info("SavoryStay backend started: JWT auth, Kafka event pipeline, ElasticEmail/SMS/WhatsApp delivery active");
    }

    /**
     * Reads the .env file from the project root and injects all variables into
     * System properties so that (1) validateRequiredSecrets() can see them and
     * (2) Spring's {@code ${VAR:}} placeholders resolve correctly.
     *
     * System-level env vars always win — .env only fills in what's missing.
     */
    private static void loadDotEnv() {
        // Search for .env in common locations
        String[] candidates = {".env", "springboot-backend/.env"};
        for (String candidate : candidates) {
            Path envFile = Path.of(candidate);
            if (!Files.exists(envFile)) continue;

            try (BufferedReader reader = Files.newBufferedReader(envFile)) {
                String line;
                int loaded = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    int eqIndex = line.indexOf('=');
                    if (eqIndex <= 0) continue;

                    String key = line.substring(0, eqIndex).trim();
                    String value = line.substring(eqIndex + 1).trim();

                    // Only set if not already present in the process environment
                    if (System.getenv(key) == null || System.getenv(key).isBlank()) {
                        System.setProperty(key, value);
                        loaded++;
                    }
                }
                if (loaded > 0) {
                    log.info("[DOTENV] Loaded {} variable(s) from {}", loaded, candidate);
                } else {
                    log.info("[DOTENV] All environment variables already set — {} not needed", candidate);
                }
                return; // Found and processed a .env file
            } catch (IOException e) {
                log.warn("[DOTENV] Could not read {}: {}", candidate, e.getMessage());
            }
        }
        log.info("[DOTENV] No .env file found — relying on system environment variables");
    }

    /**
     * Aborts startup if the required secrets are missing or unusable.
     * Reads from environment variables (primary) with a system-property fallback,
     * matching the ${JWT_SECRET:} / ${MYSQL_PASSWORD:} placeholders in application.yml.
     */
    private static void validateRequiredSecrets() {
        List<String> problems = new ArrayList<>();

        String jwtSecret = resolve("JWT_SECRET");
        if (jwtSecret == null) {
            problems.add("JWT_SECRET is not set. Generate one with `openssl rand -hex 32` and export it (see springboot-backend/.env.example).");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.add("JWT_SECRET is too short (must be at least 32 bytes). Generate one with `openssl rand -hex 32`.");
        }

        String dbPassword = resolve("MYSQL_PASSWORD");
        if (dbPassword == null) {
            problems.add("MYSQL_PASSWORD is not set. Add it to your environment (see springboot-backend/.env.example).");
        }

        if (!problems.isEmpty()) {
            System.err.println("====================================================================");
            System.err.println("SavoryStay startup ABORTED: required secrets are missing.");
            for (String p : problems) {
                System.err.println("  - " + p);
            }
            System.err.println("See springboot-backend/.env.example and DEVELOPER_GUIDE.md for setup.");
            System.err.println("====================================================================");
            throw new IllegalStateException("Missing required secrets: " + String.join("; ", problems));
        }
    }

    private static String resolve(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return (value == null || value.isBlank()) ? null : value;
    }
}
