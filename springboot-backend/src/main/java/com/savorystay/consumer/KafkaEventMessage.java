package com.savorystay.consumer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Shared payload accessors for the Kafka notification consumers.
 *
 * Consumers receive the JSON envelope produced by
 * {@link com.savorystay.service.KafkaEventPublisher} (its {@code EventEnvelope}
 * record) and read the domain payload fields through the small helpers below
 * (null-safe, string-tolerant).
 */
public final class KafkaEventMessage {

    private KafkaEventMessage() {
    }

    public static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    public static BigDecimal num(Map<String, Object> p, String key) {
        try {
            Object v = p.get(key);
            return v != null ? new BigDecimal(String.valueOf(v)) : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** Human-friendly label for an order status code. */
    public static String humanize(String status) {
        return switch (status) {
            case "NEW" -> "received";
            case "PREPARING" -> "being prepared";
            case "PACKED_READY" -> "packed & ready";
            case "COMPLETED" -> "completed";
            case "DECLINED" -> "declined";
            default -> status;
        };
    }
}
