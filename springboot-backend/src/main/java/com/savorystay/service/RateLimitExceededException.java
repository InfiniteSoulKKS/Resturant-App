package com.savorystay.service;

/**
 * Thrown when an authentication request exceeds a rate limit or hits an
 * account lockout. Mapped to HTTP 429 by GlobalExceptionHandler.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
