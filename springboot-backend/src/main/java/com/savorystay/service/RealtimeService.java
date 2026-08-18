package com.savorystay.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time event bus over Server-Sent Events.
 * Emitters are keyed by userId (customers receive personal events)
 * and by restaurantId (staff receive order events for their restaurant).
 */
@Slf4j
@Service
public class RealtimeService {

    private static final long TIMEOUT_MS = 60_000L * 5;

    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> restaurantEmitters = new ConcurrentHashMap<>();

    public SseEmitter connectUser(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        userEmitters.put(userId, emitter);
        registerLifecycle(userId, emitter, userEmitters);
        return emitter;
    }

    public SseEmitter connectRestaurant(String restaurantId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        restaurantEmitters.put(restaurantId, emitter);
        registerLifecycle(restaurantId, emitter, restaurantEmitters);
        return emitter;
    }

    private void registerLifecycle(String key, SseEmitter emitter, Map<String, SseEmitter> registry) {
        emitter.onCompletion(() -> registry.remove(key));
        emitter.onTimeout(() -> registry.remove(key));
        emitter.onError(e -> registry.remove(key));
    }

    /** Push a JSON event to a specific user (e.g. customer order status). */
    public boolean pushToUser(String userId, String eventName, Object payload) {
        SseEmitter emitter = userEmitters.get(userId);
        if (emitter == null) return false;
        return send(emitter, eventName, payload, userEmitters, userId);
    }

    /** Push a JSON event to all staff listening for a restaurant (e.g. new order). */
    public boolean pushToRestaurant(String restaurantId, String eventName, Object payload) {
        SseEmitter emitter = restaurantEmitters.get(restaurantId);
        if (emitter == null) return false;
        return send(emitter, eventName, payload, restaurantEmitters, restaurantId);
    }

    private boolean send(SseEmitter emitter, String eventName, Object payload,
                         Map<String, SseEmitter> registry, String key) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (IOException | IllegalStateException e) {
            registry.remove(key);
            emitter.complete();
            return false;
        }
    }

    public void disconnect(String userId, String restaurantId) {
        if (userId != null) userEmitters.remove(userId);
        if (restaurantId != null) restaurantEmitters.remove(restaurantId);
    }

    /** Heartbeat to keep connections alive and prune dead ones. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        userEmitters.forEach((key, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                userEmitters.remove(key);
                emitter.complete();
            }
        });
        restaurantEmitters.forEach((key, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                restaurantEmitters.remove(key);
                emitter.complete();
            }
        });
    }
}
