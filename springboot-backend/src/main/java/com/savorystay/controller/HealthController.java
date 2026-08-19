package com.savorystay.controller;

import com.savorystay.dto.MailHealthResponse;
import com.savorystay.service.ChannelDeliveryService;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Health endpoints for uptime checks, load balancers and on-demand diagnostics.
 * Publicly reachable (permitted in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final ChannelDeliveryService channelDeliveryService;

    /** Optional — null when Redis auto-config is excluded (e.g. in tests). */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String kafkaBootstrapServers;

    public HealthController(ChannelDeliveryService channelDeliveryService) {
        this.channelDeliveryService = channelDeliveryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "savory-stay-backend",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /** SMTP connectivity check — connects and authenticates (no message sent). */
    @GetMapping("/mail")
    public MailHealthResponse mailHealth() {
        return channelDeliveryService.checkMailHealth();
    }

    /** Redis connectivity check — PING / PONG with latency measurement. */
    @GetMapping("/redis")
    public Map<String, Object> redisHealth() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "redis");
        if (redisTemplate == null) {
            result.put("status", "DOWN");
            result.put("reachable", false);
            result.put("message", "Redis auto-configuration not available");
            result.put("checkedAt", LocalDateTime.now());
            return result;
        }
        try {
            long start = System.currentTimeMillis();
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            long latencyMs = System.currentTimeMillis() - start;
            result.put("status", "PONG".equals(pong) ? "UP" : "DOWN");
            result.put("reachable", true);
            result.put("latencyMs", latencyMs);
            result.put("message", "Redis connection OK");
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("reachable", false);
            result.put("message", "Redis unavailable: " + e.getMessage());
        }
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    /** Kafka broker connectivity check — lists topics with a short timeout. */
    @GetMapping("/kafka")
    public Map<String, Object> kafkaHealth() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "kafka");
        result.put("bootstrapServers", kafkaBootstrapServers);
        try {
            Properties props = new Properties();
            props.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
            try (AdminClient admin = AdminClient.create(props)) {
                long start = System.currentTimeMillis();
                var topics = admin.listTopics().names().get(5, TimeUnit.SECONDS).size();
                long latencyMs = System.currentTimeMillis() - start;
                result.put("status", "UP");
                result.put("reachable", true);
                result.put("topicCount", topics);
                result.put("latencyMs", latencyMs);
                result.put("message", "Kafka broker reachable, " + topics + " topics");
            }
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("reachable", false);
            result.put("message", "Kafka unreachable: " + e.getMessage());
        }
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }
}
