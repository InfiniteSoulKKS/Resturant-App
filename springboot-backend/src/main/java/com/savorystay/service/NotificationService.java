package com.savorystay.service;

import com.savorystay.entity.Notification;
import com.savorystay.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates notifications, persists them, pushes them in real-time over SSE,
 * and dispatches them over SMS / WhatsApp / Email in real time via
 * {@link ChannelDeliveryService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RealtimeService realtimeService;
    private final ChannelDeliveryService deliveryService;
    private final EmailTemplateService emailTemplateService;

    /** Overload without explicit delivery targets (staff broadcasts etc.). */
    public Notification create(String userId, String restaurantId, String orderId,
                               String title, String message, String type, String channel) {
        return create(userId, restaurantId, orderId, title, message, type, channel, null, null);
    }

    /**
     * Create + persist a notification, push it in real time over SSE, and
     * dispatch it to the requested channels (APP / SMS / WHATSAPP / EMAIL).
     * Delivery targets carry the customer's phone/email for real-time dispatch.
     */
    public Notification create(String userId, String restaurantId, String orderId,
                               String title, String message, String type, String channel,
                               String deliveryPhone, String deliveryEmail) {
        Notification n = Notification.builder()
                .userId(userId)
                .restaurantId(restaurantId)
                .orderId(orderId)
                .title(title)
                .message(message)
                .type(type)
                .channel(channel != null ? channel : "APP")
                .deliveryPhone(deliveryPhone)
                .deliveryEmail(deliveryEmail)
                .read(false)
                .status("PENDING")
                .attemptCount(0)
                .build();
        Notification saved = notificationRepository.save(n);

        // Push over SSE (in-app channel) immediately — real-time to browser
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("title", saved.getTitle());
        payload.put("message", saved.getMessage());
        payload.put("type", saved.getType());
        payload.put("channel", saved.getChannel());
        payload.put("orderId", saved.getOrderId());
        payload.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null);

        if (userId != null) realtimeService.pushToUser(userId, "notification", payload);
        if (restaurantId != null) realtimeService.pushToRestaurant(restaurantId, "notification", payload);

        // Dispatch other channels (SMS / WhatsApp / Email) in real time, tracking delivery status
        return dispatch(saved);
    }

    /**
     * Attempts real-time delivery across all configured channels.
     * Marks the notification SENT -> DELIVERED, or FAILED after exceeding attempts.
     */
    public Notification dispatch(Notification n) {
        n.setAttemptCount(n.getAttemptCount() + 1);
        try {
            dispatchChannels(n);
            n.setSentAt(LocalDateTime.now());
            n.setDeliveredAt(LocalDateTime.now());
            n.setStatus("DELIVERED");
        } catch (Exception e) {
            n.setStatus("FAILED");
            n.setFailedAt(LocalDateTime.now());
            log.error("Notification delivery failed for {}: {}", n.getId(), e.getMessage());
        }
        return notificationRepository.save(n);
    }

    /**
     * Each channel is isolated: a failing SMS must not drop the WhatsApp or
     * Email alert, and vice versa. Failures are logged but never abort the loop.
     */
    private void dispatchChannels(Notification n) {
        String[] channels = (n.getChannel() != null && !n.getChannel().isBlank())
                ? n.getChannel().split(",")
                : new String[]{"APP"};
        for (String ch : channels) {
            try {
                switch (ch.trim().toUpperCase()) {
                    case "SMS" -> {
                        if (n.getDeliveryPhone() != null && !n.getDeliveryPhone().isBlank()) {
                            deliveryService.sendSms(n.getDeliveryPhone(), smsBody(n));
                        } else {
                            log.info("[SMS-DISPATCH] No delivery phone for notification {}", n.getId());
                        }
                    }
                    case "WHATSAPP" -> {
                        if (n.getDeliveryPhone() != null && !n.getDeliveryPhone().isBlank()) {
                            deliveryService.sendWhatsApp(n.getDeliveryPhone(), n.getMessage());
                        } else {
                            log.info("[WHATSAPP-DISPATCH] No delivery phone for notification {}", n.getId());
                        }
                    }
                    case "EMAIL" -> {
                        if (n.getDeliveryEmail() != null && !n.getDeliveryEmail().isBlank()) {
                            // Branded HTML template (domain consumers send richer, structured
                            // emails directly; this is the generic branded fallback).
                            deliveryService.sendHtmlEmail(n.getDeliveryEmail(), n.getTitle(),
                                    emailTemplateService.genericEmail(n.getTitle(), n.getMessage()));
                        } else {
                            log.info("[EMAIL-DISPATCH] No delivery email for notification {}", n.getId());
                        }
                    }
                    default -> { /* APP push handled by SSE */ }
                }
            } catch (Exception e) {
                log.warn("[{}] channel failed for notification {}: {}", ch, n.getId(), e.getMessage());
            }
        }
    }

    private String smsBody(Notification n) {
        // SMS is length-limited; keep it short
        String msg = n.getMessage() != null ? n.getMessage() : n.getTitle();
        return msg.length() > 160 ? msg.substring(0, 157) + "..." : msg;
    }

    public List<Notification> listForUser(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> listForRestaurant(String restaurantId) {
        return notificationRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> {
            n.setRead(true);
            n.setStatus("READ");
        });
        notificationRepository.saveAll(unread);
    }
}
