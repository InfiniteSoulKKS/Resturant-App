package com.savorystay.controller;

import com.savorystay.dto.MailHealthResponse;
import com.savorystay.service.ChannelDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health endpoints for uptime checks, load balancers and on-demand diagnostics.
 * Publicly reachable (permitted in SecurityConfig); previously returned 403
 * because Spring Security's MvcRequestMatcher does not match paths that have
 * no controller handler.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final ChannelDeliveryService channelDeliveryService;

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

    /**
     * On-demand SMTP connectivity check — connects to the configured mail server
     * and authenticates (no message sent). "Not configured" and failed checks
     * are reported in the body (status field) rather than as HTTP errors.
     */
    @GetMapping("/mail")
    public MailHealthResponse mailHealth() {
        return channelDeliveryService.checkMailHealth();
    }
}
