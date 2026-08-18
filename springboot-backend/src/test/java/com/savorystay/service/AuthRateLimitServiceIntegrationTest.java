package com.savorystay.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link AuthRateLimitService} against a real Redis
 * (via Testcontainers), verifying the shared lockout, counter TTLs, throttling,
 * and the fail-open behavior when Redis is unreachable.
 *
 * The whole class is auto-skipped when Docker is unavailable
 * ({@code @Testcontainers(disabledWithoutDocker = true)}).
 */
@Testcontainers(disabledWithoutDocker = true)
class AuthRateLimitServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private StringRedisTemplate template;
    private AuthRateLimitService service;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        service = new AuthRateLimitService(template);
        applyDefaults(service);
    }

    private static void applyDefaults(AuthRateLimitService s) {
        ReflectionTestUtils.setField(s, "maxLoginFailures", 5);
        ReflectionTestUtils.setField(s, "failureWindowMinutes", 15L);
        ReflectionTestUtils.setField(s, "lockoutMinutes", 15L);
        ReflectionTestUtils.setField(s, "otpSendMax", 5);
        ReflectionTestUtils.setField(s, "otpSendWindowMinutes", 15L);
        ReflectionTestUtils.setField(s, "otpSendIpMax", 10);
        ReflectionTestUtils.setField(s, "otpVerifyMax", 10);
    }

    @Test
    void lockoutThresholdLocksInSharedRedis() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("user:alice");
        }
        assertThrows(RateLimitExceededException.class, () -> service.throwIfLocked("user:alice"));
    }

    @Test
    void recordSuccessUnlocksAccount() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("user:bob");
        }
        assertThrows(RateLimitExceededException.class, () -> service.throwIfLocked("user:bob"));

        service.recordSuccess("user:bob");
        assertDoesNotThrow(() -> service.throwIfLocked("user:bob"));
    }

    @Test
    void otpSendThrottledAfterBudget() {
        for (int i = 0; i < 5; i++) {
            service.recordOtpSend("user:carol", "10.0.0.1");
        }
        assertThrows(RateLimitExceededException.class,
                () -> service.throwIfOtpSendExceeded("user:carol", "10.0.0.1"));
    }

    @Test
    void countersCarryATtlInRedis() {
        service.recordOtpSend("user:dave", "10.0.0.2");
        Long ttl = template.getExpire("savory:ratelimit:cnt:send:user:dave", TimeUnit.SECONDS);
        assertNotNull(ttl, "Redis counter should exist");
        assertTrue(ttl > 0, "Redis counter should carry a positive TTL");
    }

    @Test
    void failsOpenWhenRedisIsUnreachable() {
        // Point a service at a closed port: every Redis call fails -> must NOT throw.
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 1);
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();
        AuthRateLimitService deadService = new AuthRateLimitService(deadTemplate);
        applyDefaults(deadService);

        assertDoesNotThrow(() -> deadService.throwIfLocked("user:x"));
        assertDoesNotThrow(() -> deadService.recordFailure("user:x"));
        assertDoesNotThrow(() -> deadService.throwIfOtpSendExceeded("user:x", "1.2.3.4"));
        assertDoesNotThrow(() -> deadService.recordOtpSend("user:x", "1.2.3.4"));
    }
}
