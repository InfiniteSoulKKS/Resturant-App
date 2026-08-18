package com.savorystay.common;

import java.util.UUID;

/**
 * Collision-safe ID generation for entities that use string primary keys.
 *
 * Replaces the previous {@code System.currentTimeMillis()}-based suffixes,
 * which could collide when two rows were created in the same millisecond and
 * surfaced as duplicate-primary-key errors under load.
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    /** e.g. {@code newId("USR")} → {@code "USR_3F9A2C1E8B4D5F6A7C8D9E0F"}. */
    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    /** Short human-friendly suffix for order numbers etc. (6 uppercase hex chars). */
    public static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
