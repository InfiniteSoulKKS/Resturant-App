package com.savorystay.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthRateLimitService} using a mocked Redis.
 * Covers the lockout threshold, TTL expiry (via the Redis ops that are invoked),
 * OTP send/verify throttles, and the fail-open behavior when Redis errors.
 */
@ExtendWith(MockitoExtension.class)
class AuthRateLimitServiceTest {

    private static final String NS = "savory:ratelimit:";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private AuthRateLimitService service;

    @BeforeEach
    void setUp() {
        // lenient: not every test touches opsForValue() (e.g. lockout checks only use hasKey).
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        // Defaults (same as application.yml) — set explicitly so tests are deterministic.
        ReflectionTestUtils.setField(service, "maxLoginFailures", 5);
        ReflectionTestUtils.setField(service, "failureWindowMinutes", 15L);
        ReflectionTestUtils.setField(service, "lockoutMinutes", 15L);
        ReflectionTestUtils.setField(service, "otpSendMax", 5);
        ReflectionTestUtils.setField(service, "otpSendWindowMinutes", 15L);
        ReflectionTestUtils.setField(service, "otpSendIpMax", 10);
        ReflectionTestUtils.setField(service, "otpVerifyMax", 10);
    }

    // ============================ LOCKOUT THRESHOLD ============================

    @Test
    void recordFailure_locksAccountAfterThreshold() {
        // Configure a low threshold for this test.
        ReflectionTestUtils.setField(service, "maxLoginFailures", 2);
        String failKey = NS + "fail:user:alice";
        when(valueOps.increment(failKey)).thenReturn(1L, 2L);

        service.recordFailure("user:alice");
        // First failure: counter created with a TTL, but no lock yet.
        verify(redis).expire(failKey, 15, TimeUnit.MINUTES);
        verify(valueOps, never())
                .set(NS + "lock:user:alice", "1", 15, TimeUnit.MINUTES);

        service.recordFailure("user:alice");
        // Second failure reaches the threshold -> lock set, counter reset.
        verify(valueOps).set(NS + "lock:user:alice", "1", 15, TimeUnit.MINUTES);
        verify(redis).delete(failKey);

        when(redis.hasKey(NS + "lock:user:alice")).thenReturn(true);
        assertThrows(RateLimitExceededException.class, () -> service.throwIfLocked("user:alice"));
    }

    @Test
    void throwIfLocked_allowsWhenNotLocked() {
        when(redis.hasKey(anyString())).thenReturn(false);
        assertDoesNotThrow(() -> service.throwIfLocked("user:alice", "ip:1.2.3.4"));
    }

    @Test
    void throwIfLocked_throwsWhenLocked() {
        when(redis.hasKey(NS + "lock:user:alice")).thenReturn(true);
        assertThrows(RateLimitExceededException.class, () -> service.throwIfLocked("user:alice"));
    }

    @Test
    void recordSuccess_clearsFailuresAndLock() {
        service.recordSuccess("user:alice");
        verify(redis).delete(NS + "fail:user:alice");
        verify(redis).delete(NS + "lock:user:alice");
    }

    // ============================ OTP SEND THROTTLE ============================

    @Test
    void throwIfOtpSendExceeded_throwsWhenOverBudget() {
        when(valueOps.get(NS + "cnt:send:user@example.com")).thenReturn("5"); // >= otpSendMax
        assertThrows(RateLimitExceededException.class,
                () -> service.throwIfOtpSendExceeded("user@example.com", "1.2.3.4"));
    }

    @Test
    void throwIfOtpSendExceeded_throwsOnPerIpCap() {
        when(valueOps.get(NS + "cnt:send:user@example.com")).thenReturn("1");
        when(valueOps.get(NS + "cnt:ip:1.2.3.4")).thenReturn("10"); // >= otpSendIpMax
        assertThrows(RateLimitExceededException.class,
                () -> service.throwIfOtpSendExceeded("user@example.com", "1.2.3.4"));
    }

    @Test
    void throwIfOtpSendExceeded_allowsUnderBudget() {
        when(valueOps.get(anyString())).thenReturn("1");
        assertDoesNotThrow(() -> service.throwIfOtpSendExceeded("user@example.com", "1.2.3.4"));
    }

    @Test
    void recordOtpSend_setsTtlOnFirstIncrement() {
        when(valueOps.increment(NS + "cnt:send:user@example.com")).thenReturn(1L);
        when(valueOps.increment(NS + "cnt:ip:1.2.3.4")).thenReturn(1L);
        service.recordOtpSend("user@example.com", "1.2.3.4");
        verify(redis).expire(NS + "cnt:send:user@example.com", 15, TimeUnit.MINUTES);
        verify(redis).expire(NS + "cnt:ip:1.2.3.4", 1, TimeUnit.HOURS);
    }

    // ============================ OTP VERIFY THROTTLE ============================

    @Test
    void throwIfOtpVerifyExceeded_throwsWhenOverBudget() {
        when(valueOps.get(NS + "cnt:verify:user@example.com")).thenReturn("10"); // >= otpVerifyMax
        assertThrows(RateLimitExceededException.class,
                () -> service.throwIfOtpVerifyExceeded("user@example.com", "1.2.3.4"));
    }

    @Test
    void recordOtpVerify_setsTtl() {
        when(valueOps.increment(NS + "cnt:verify:user@example.com")).thenReturn(1L);
        when(valueOps.increment(NS + "cnt:verifyip:1.2.3.4")).thenReturn(1L);
        service.recordOtpVerify("user@example.com", "1.2.3.4");
        verify(redis).expire(NS + "cnt:verify:user@example.com", 15, TimeUnit.MINUTES);
        verify(redis).expire(NS + "cnt:verifyip:1.2.3.4", 15, TimeUnit.MINUTES);
    }

    // ============================ FAIL-OPEN ON REDIS ERRORS ============================

    @Test
    void throwIfLocked_failsOpenOnRedisError() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> service.throwIfLocked("user:alice"));
    }

    @Test
    void recordFailure_failsOpenOnRedisError() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> service.recordFailure("user:alice"));
    }

    @Test
    void throwIfOtpSendExceeded_failsOpenOnRedisError() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> service.throwIfOtpSendExceeded("user@example.com", "1.2.3.4"));
    }

    @Test
    void recordOtpSend_failsOpenOnRedisError() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> service.recordOtpSend("user@example.com", "1.2.3.4"));
    }

    // ============================ CLIENT IP ============================

    @Test
    void clientIp_honorsXForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");
        assertEquals("203.0.113.9", AuthRateLimitService.clientIp(request));
    }

    @Test
    void clientIp_fallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.50");
        assertEquals("192.168.1.50", AuthRateLimitService.clientIp(request));
    }
}
