package com.savorystay.controller;

import com.savorystay.dto.NotificationResponse;
import com.savorystay.entity.Notification;
import com.savorystay.security.AuthContext;
import com.savorystay.security.JwtTokenProvider;
import com.savorystay.service.NotificationService;
import com.savorystay.service.RealtimeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;
    private final RealtimeService realtimeService;
    private final AuthContext authContext;
    private final JwtTokenProvider tokenProvider;

    /**
     * SSE real-time stream.
     * EventSource cannot set Authorization headers, so the JWT is passed as ?token=.
     * - Customers are subscribed as their userId.
     * - Staff (with restaurantId) are additionally subscribed to their restaurant channel.
     */
    @GetMapping(value = "/api/v1/realtime/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token) {
        if (!tokenProvider.validateToken(token)) {
            SseEmitter denied = new SseEmitter(0L);
            denied.completeWithError(new SecurityException("Invalid token"));
            return denied;
        }

        String userId = tokenProvider.getUserIdFromJWT(token);
        String role = tokenProvider.getRoleFromJWT(token);
        String restaurantId = tokenProvider.getRestaurantIdFromJWT(token);

        // Customers: subscribe to their user channel.
        if (userId != null && "ROLE_CUSTOMER".equals(role)) {
            return realtimeService.connectUser(userId);
        }

        // Staff: subscribe to their restaurant channel (admin/manager/chef).
        if (restaurantId != null) {
            return realtimeService.connectRestaurant(restaurantId);
        }

        SseEmitter fallback = new SseEmitter(0L);
        fallback.complete();
        return fallback;
    }

    /**
     * Authenticated: list my notifications.
     */
    @GetMapping("/api/v1/notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> myNotifications(HttpServletRequest request) {
        String userId = authContext.userId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }
        List<Notification> notifications = notificationService.listForUser(userId);

        // Staff also see restaurant-scoped broadcasts (created with userId = null)
        String restaurantId = authContext.restaurantId(request);
        if (restaurantId != null) {
            Set<String> seenIds = notifications.stream().map(Notification::getId).collect(Collectors.toSet());
            for (Notification n : notificationService.listForRestaurant(restaurantId)) {
                if (seenIds.add(n.getId())) notifications.add(n);
            }
            notifications.sort(Comparator.comparing(Notification::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        List<NotificationResponse> dtos = notifications.stream().map(NotificationResponse::from).toList();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "notifications", dtos,
                "unread", notificationService.unreadCount(userId)));
    }

    @PostMapping("/api/v1/notifications/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markAllRead(HttpServletRequest request) {
        String userId = authContext.userId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
