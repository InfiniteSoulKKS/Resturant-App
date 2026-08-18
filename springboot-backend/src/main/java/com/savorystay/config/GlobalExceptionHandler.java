package com.savorystay.config;

import com.savorystay.service.RateLimitExceededException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.security.access.AccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Global mapping for exceptions that escape controllers, producing a single
 * consistent JSON shape:
 *
 * <pre>{ "success": false, "message": "..." }</pre>
 *
 * The message is user-safe: business rules and validation errors surface
 * their reason, while unexpected failures return a generic message (the full
 * stack is logged server-side, never sent to the client).
 *
 * Controllers that already catch and map their own exceptions keep working —
 * this advice only handles what escapes them.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    /** Business-rule / input violations (e.g. "Menu item not found in this restaurant"). */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid request");
    }

    /**
     * Bean-validation failures on @Valid request bodies (@NotBlank, @Email, @Min…).
     * All offending fields are reported in a single readable message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message);
    }

    /** Malformed JSON bodies / bad query-param types are client errors, not 500s. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    /** Missing required params / path vars are client errors, not 500s. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ResponseEntity<Map<String, Object>> handleMissingParam(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Missing required request parameter");
    }

    /** Wrong HTTP method on an existing path → 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    /** Wrong Content-Type → 415. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
    }

    /** Missing resources surfaced as 404. */
    @ExceptionHandler({NoSuchElementException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception e) {
        return error(HttpStatus.NOT_FOUND, "Resource not found");
    }

    /** Authorization failures must never masquerade as generic errors. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SecurityException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage() != null ? e.getMessage() : "Forbidden");
    }

    /**
     * Method-security denials ({@code @PreAuthorize}) raise Spring Security's
     * AccessDeniedException, which would otherwise fall into the catch-all
     * below and surface as a misleading 500. An authenticated caller lacking
     * the required role gets a proper 403 — e.g. a customer hitting the
     * staff-only order queue.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "Forbidden");
    }

    /** Unique-constraint / FK violations (duplicate username, email, etc.). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "A record with these details already exists");
    }

    /**
     * Optimistic-lock conflicts (e.g. two kitchen actions deducting the same
     * ingredient concurrently). The operation is safe to retry — 409 tells the
     * client exactly that.
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(Exception e) {
        log.warn("Optimistic-lock conflict: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "Concurrent update detected. Please retry.");
    }

    /**
     * SSE streams time out whenever a client disconnects or the emitter timeout
     * elapses — NORMAL lifecycle, not an error. The response is already committed
     * as text/event-stream, so writing a JSON error body here would itself fail;
     * we just close the stream silently (no 5xx, no stack trace).
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.debug("SSE stream closed on timeout (client disconnected)");
    }

    /** Unexpected failures — log the real cause, return a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message));
    }
}
