package com.savorystay.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed rate limiting + account lockout for authentication endpoints.
 * Shared state means the limits hold across multiple backend instances.
 *
 * Design (fixed-window counters + TTL lockouts, atomic via Redis INCR/SET/EXPIRE):
 *  - <b>Failure lockout</b> (login / OTP login): INCR a per-key failure counter;
 *    when it reaches the threshold, SET a lock key with a TTL. recordSuccess()
 *    clears the counter + lock.
 *  - <b>Window throttles</b> (OTP send / OTP verify): INCR per-key counters with a
 *    TTL so they auto-expire, preventing SMS/email bombing and OTP brute-forcing.
 *
 * Fail-open: if Redis is unavailable, requests are allowed (and logged) so an
 * outage never takes down authentication. In production, point REDIS_HOST at a
 * shared Redis so every instance agrees on the same counters and lockouts.
 */
@Slf4j
@Component
public class AuthRateLimitService {

    private static final String NS = "savory:ratelimit:";

    private final StringRedisTemplate redis;

    @Value("${security.rate-limit.max-login-failures:5}")
    private int maxLoginFailures;

    @Value("${security.rate-limit.failure-window-minutes:15}")
    private long failureWindowMinutes;

    @Value("${security.rate-limit.lockout-minutes:15}")
    private long lockoutMinutes;

    @Value("${security.rate-limit.otp-send-max:5}")
    private int otpSendMax;

    @Value("${security.rate-limit.otp-send-window-minutes:15}")
    private long otpSendWindowMinutes;

    @Value("${security.rate-limit.otp-send-ip-max:10}")
    private int otpSendIpMax;

    @Value("${security.rate-limit.otp-verify-max:10}")
    private int otpVerifyMax;

    public AuthRateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ============================ FAILURE LOCKOUT ============================

    /** Throws if any key is currently locked. Call before processing auth. */
    public void throwIfLocked(String... keys) {
        for (String key : keys) {
            try {
                if (Boolean.TRUE.equals(redis.hasKey(NS + "lock:" + key))) {
                    throw new RateLimitExceededException(
                            "Too many failed attempts. Account is temporarily locked. Please try again later.");
                }
            } catch (RateLimitExceededException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[rate-limit] Redis unavailable, allowing request (fail-open): {}", e.getMessage());
                return;
            }
        }
    }

    /** Records a failed attempt for each key; locks a key once the threshold is hit. */
    public void recordFailure(String... keys) {
        for (String key : keys) {
            try {
                String failKey = NS + "fail:" + key;
                Long count = redis.opsForValue().increment(failKey);
                if (count != null && count == 1L) {
                    redis.expire(failKey, failureWindowMinutes, TimeUnit.MINUTES);
                }
                if (count != null && count >= maxLoginFailures) {
                    redis.opsForValue().set(NS + "lock:" + key, "1", lockoutMinutes, TimeUnit.MINUTES);
                    redis.delete(failKey);
                    log.warn("[rate-limit] Locked '{}' for {} minutes after {} failed attempts",
                            key, lockoutMinutes, maxLoginFailures);
                }
            } catch (Exception e) {
                log.warn("[rate-limit] Redis unavailable, skipping failure record (fail-open): {}", e.getMessage());
            }
        }
    }

    /** Clears failure history + lockout for the keys (on successful auth). */
    public void recordSuccess(String... keys) {
        for (String key : keys) {
            try {
                redis.delete(NS + "fail:" + key);
                redis.delete(NS + "lock:" + key);
            } catch (Exception e) {
                log.warn("[rate-limit] Redis unavailable, skipping success clear (fail-open): {}", e.getMessage());
            }
        }
    }

    // ============================ WINDOW THROTTLES ============================

    /** Throws if the identity or IP has exhausted its OTP-send budget. */
    public void throwIfOtpSendExceeded(String identifier, String ip) {
        throwIfExceeded("send:" + identifier, otpSendMax, otpSendWindowMinutes, TimeUnit.MINUTES);
        throwIfExceeded("ip:" + ip, otpSendIpMax, 1, TimeUnit.HOURS);
    }

    /** Records a successful OTP dispatch for the identity + IP. */
    public void recordOtpSend(String identifier, String ip) {
        record("send:" + identifier, otpSendWindowMinutes, TimeUnit.MINUTES);
        record("ip:" + ip, 1, TimeUnit.HOURS);
    }

    /** Throws if the identity/IP has made too many verify attempts in the window. */
    public void throwIfOtpVerifyExceeded(String identifier, String ip) {
        throwIfExceeded("verify:" + identifier, otpVerifyMax, failureWindowMinutes, TimeUnit.MINUTES);
        throwIfExceeded("verifyip:" + ip, otpVerifyMax, failureWindowMinutes, TimeUnit.MINUTES);
    }

    /** Records a verify attempt (success or failure — the budget counts both). */
    public void recordOtpVerify(String identifier, String ip) {
        record("verify:" + identifier, failureWindowMinutes, TimeUnit.MINUTES);
        record("verifyip:" + ip, failureWindowMinutes, TimeUnit.MINUTES);
    }

    private void throwIfExceeded(String key, int max, long window, TimeUnit unit) {
        try {
            String value = redis.opsForValue().get(NS + "cnt:" + key);
            long count = value == null ? 0 : Long.parseLong(value);
            if (count >= max) {
                throw new RateLimitExceededException("Too many requests. Please try again later.");
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[rate-limit] Redis unavailable, allowing request (fail-open): {}", e.getMessage());
        }
    }

    private void record(String key, long window, TimeUnit unit) {
        try {
            String cntKey = NS + "cnt:" + key;
            Long count = redis.opsForValue().increment(cntKey);
            if (count != null && count == 1L) {
                redis.expire(cntKey, window, unit);
            }
        } catch (Exception e) {
            log.warn("[rate-limit] Redis unavailable, skipping record (fail-open): {}", e.getMessage());
        }
    }

    /** Best-effort client IP: honors X-Forwarded-For, falls back to remote address. */
    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
