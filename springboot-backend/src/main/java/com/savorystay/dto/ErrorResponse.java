package com.savorystay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standardized error response for all API errors (P0.20).
 * Frontend should use {@code code}, not parse human-readable messages.
 *
 * <pre>{
 *   "success": false,
 *   "code": "PLATE_CAPACITY_EXCEEDED",
 *   "message": "Only 2 plates are remaining.",
 *   "requestId": "req-123",
 *   "timestamp": "..."
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private boolean success = false;

    /** Machine-readable error code — frontend uses this, not the message. */
    private String code;

    /** Human-readable message safe for end users. */
    private String message;

    /** Request correlation ID for debugging. */
    private String requestId;

    @Builder.Default
    private String timestamp = LocalDateTime.now().toString();

    // ─── ERROR CODE CONSTANTS ──────────────────────────────────────

    public static final String AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    public static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    public static final String TENANT_ACCESS_DENIED = "TENANT_ACCESS_DENIED";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    public static final String ORDER_INVALID_TRANSITION = "ORDER_INVALID_TRANSITION";
    public static final String ORDER_ALREADY_CANCELLED = "ORDER_ALREADY_CANCELLED";
    public static final String ORDER_ALREADY_COMPLETED = "ORDER_ALREADY_COMPLETED";

    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_UNKNOWN = "PAYMENT_UNKNOWN";
    public static final String PAYMENT_ALREADY_PROCESSED = "PAYMENT_ALREADY_PROCESSED";
    public static final String PAYMENT_AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";

    public static final String REFUND_ALREADY_EXISTS = "REFUND_ALREADY_EXISTS";
    public static final String REFUND_FAILED = "REFUND_FAILED";

    public static final String INVENTORY_INSUFFICIENT = "INVENTORY_INSUFFICIENT";
    public static final String INVENTORY_RESERVATION_CONFLICT = "INVENTORY_RESERVATION_CONFLICT";

    public static final String PLATE_CAPACITY_EXCEEDED = "PLATE_CAPACITY_EXCEEDED";

    public static final String TABLE_SLOT_FULL = "TABLE_SLOT_FULL";
    public static final String TABLE_RESERVATION_CONFLICT = "TABLE_RESERVATION_CONFLICT";

    public static final String PREORDER_CUTOFF_PASSED = "PREORDER_CUTOFF_PASSED";
    public static final String PREORDER_RESTAURANT_CLOSED = "PREORDER_RESTAURANT_CLOSED";
    public static final String DISH_NOT_AVAILABLE = "DISH_NOT_AVAILABLE";

    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // ─── FACTORY METHODS ──────────────────────────────────────────

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static ErrorResponse unauthorized(String message) {
        return of(AUTH_UNAUTHORIZED, message != null ? message : "Authentication required");
    }

    public static ErrorResponse forbidden(String message) {
        return of(AUTH_FORBIDDEN, message != null ? message : "You don't have permission to access this resource");
    }

    public static ErrorResponse notFound(String message) {
        return of(RESOURCE_NOT_FOUND, message != null ? message : "Resource not found");
    }

    public static ErrorResponse badRequest(String code, String message) {
        return of(code, message);
    }

    public static ErrorResponse conflict(String code, String message) {
        return of(code, message);
    }

    public static ErrorResponse serverError(String message) {
        return of(INTERNAL_ERROR, message != null ? message : "An unexpected error occurred. Please try again.");
    }
}
