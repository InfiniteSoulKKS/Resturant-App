package com.savorystay.config;

import com.savorystay.dto.ErrorResponse;
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
 * consistent JSON shape with error codes (P0.20):
 *
 * <pre>{ "success": false, "code": "...", "message": "..." }</pre>
 *
 * The message is user-safe: business rules and validation errors surface
 * their reason, while unexpected failures return a generic message (the full
 * stack is logged server-side, never sent to the client).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ErrorResponse.RATE_LIMITED, e.getMessage());
    }

    /** Business-rule / input violations (e.g. "Menu item not found in this restaurant"). */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Invalid request";
        String code = resolveBusinessErrorCode(msg);
        return error(HttpStatus.BAD_REQUEST, code, msg);
    }

    /** Order state machine transition violations → 409 Conflict. */
    @ExceptionHandler(OrderStateException.class)
    public ResponseEntity<ErrorResponse> handleOrderState(OrderStateException e) {
        return error(HttpStatus.CONFLICT, ErrorResponse.ORDER_INVALID_TRANSITION, e.getMessage());
    }

    /**
     * Bean-validation failures on @Valid request bodies (@NotBlank, @Email, @Min…).
     * All offending fields are reported in a single readable message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, ErrorResponse.VALIDATION_ERROR,
                message.isEmpty() ? "Validation failed" : message);
    }

    /** Malformed JSON bodies / bad query-param types are client errors, not 500s. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleUnreadableBody(Exception e) {
        return error(HttpStatus.BAD_REQUEST, ErrorResponse.VALIDATION_ERROR, "Malformed request");
    }

    /** Missing required params / path vars are client errors, not 500s. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ResponseEntity<ErrorResponse> handleMissingParam(Exception e) {
        return error(HttpStatus.BAD_REQUEST, ErrorResponse.VALIDATION_ERROR, "Missing required request parameter");
    }

    /** Wrong HTTP method on an existing path → 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, ErrorResponse.VALIDATION_ERROR, "Method not allowed");
    }

    /** Wrong Content-Type → 415. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.VALIDATION_ERROR, "Unsupported media type");
    }

    /** Missing resources surfaced as 404. */
    @ExceptionHandler({NoSuchElementException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        return error(HttpStatus.NOT_FOUND, ErrorResponse.RESOURCE_NOT_FOUND, "Resource not found");
    }

    /** Authorization failures must never masquerade as generic errors. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(SecurityException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank() || msg.contains("Forbidden")) {
            msg = "You don't have permission to perform this action.";
        }
        String code = msg.contains("tenant") || msg.contains("restaurant")
                ? ErrorResponse.TENANT_ACCESS_DENIED : ErrorResponse.AUTH_FORBIDDEN;
        return error(HttpStatus.FORBIDDEN, code, msg);
    }

    /**
     * Method-security denials ({@code @PreAuthorize}) raise Spring Security's
     * AccessDeniedException, which would otherwise fall into the catch-all
     * below and surface as a misleading 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank() || msg.contains("Denied")) {
            msg = "You don't have permission to perform this action. Please check your account role and try again.";
        }
        return error(HttpStatus.FORBIDDEN, ErrorResponse.AUTH_FORBIDDEN, msg);
    }

    /** Unique-constraint / FK violations (duplicate username, email, etc.). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, ErrorResponse.VALIDATION_ERROR, "A record with these details already exists");
    }

    /**
     * Optimistic-lock conflicts (e.g. two kitchen actions deducting the same
     * ingredient concurrently). The operation is safe to retry — 409 tells the
     * client exactly that.
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception e) {
        log.warn("Optimistic-lock conflict: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, ErrorResponse.INVENTORY_RESERVATION_CONFLICT,
                "Concurrent update detected. Please retry.");
    }

    /**
     * SSE streams time out whenever a client disconnects or the emitter timeout
     * elapses — NORMAL lifecycle, not an error. The response is already committed
     * as text/event-stream, so writing a JSON error body here would itself fail;
     * we just close the stream silently.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.debug("SSE stream closed on timeout (client disconnected)");
    }

    /** Unexpected failures — log the real cause, return a generic message. Never expose stack traces. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again.");
    }

    // ─── INTERNAL HELPERS ──────────────────────────────────────────

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }

    /**
     * Attempt to map a business error message to a known error code.
     * This allows exception handlers that already catch IllegalArgumentException
     * to still return meaningful codes to the frontend.
     */
    private static String resolveBusinessErrorCode(String message) {
        if (message == null) return ErrorResponse.VALIDATION_ERROR;
        String lower = message.toLowerCase();
        if (lower.contains("plate") && (lower.contains("remaining") || lower.contains("sold out") || lower.contains("capacity"))) {
            return ErrorResponse.PLATE_CAPACITY_EXCEEDED;
        }
        if (lower.contains("table") && (lower.contains("available") || lower.contains("full"))) {
            return ErrorResponse.TABLE_SLOT_FULL;
        }
        if (lower.contains("cutoff")) {
            return ErrorResponse.PREORDER_CUTOFF_PASSED;
        }
        if (lower.contains("closed") || lower.contains("holiday")) {
            return ErrorResponse.PREORDER_RESTAURANT_CLOSED;
        }
        if (lower.contains("sold out") || lower.contains("not available")) {
            return ErrorResponse.DISH_NOT_AVAILABLE;
        }
        if (lower.contains("inventory") || lower.contains("insufficient")) {
            return ErrorResponse.INVENTORY_INSUFFICIENT;
        }
        if (lower.contains("payment") && lower.contains("amount")) {
            return ErrorResponse.PAYMENT_AMOUNT_MISMATCH;
        }
        if (lower.contains("cash") && lower.contains("cannot be confirmed")) {
            return ErrorResponse.PAYMENT_FAILED;
        }
        if (lower.contains("already paid")) {
            return ErrorResponse.PAYMENT_ALREADY_PROCESSED;
        }
        if (lower.contains("refund") && lower.contains("already")) {
            return ErrorResponse.REFUND_ALREADY_EXISTS;
        }
        if (lower.contains("not found")) {
            return ErrorResponse.RESOURCE_NOT_FOUND;
        }
        if (lower.contains("forbidden") || lower.contains("not authorized") || lower.contains("not the order owner")) {
            return ErrorResponse.AUTH_FORBIDDEN;
        }
        if (lower.contains("restaurant") && lower.contains("offline")) {
            return ErrorResponse.VALIDATION_ERROR;
        }
        return ErrorResponse.VALIDATION_ERROR;
    }
}
