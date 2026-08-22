package com.savorystay.config;

/**
 * Thrown when an order state transition is invalid (P0.3).
 * The GlobalExceptionHandler maps this to HTTP 409 with ORDER_INVALID_TRANSITION.
 */
public class OrderStateException extends RuntimeException {

    private final String errorCode;

    public OrderStateException(String message) {
        super(message);
        this.errorCode = "ORDER_INVALID_TRANSITION";
    }

    public OrderStateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
