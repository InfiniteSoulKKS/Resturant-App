package com.savorystay.dto;

import com.savorystay.entity.Notification;

import java.time.LocalDateTime;

/**
 * View model for a notification.
 * Delivery routing targets (phone/email) are internal dispatch details and are
 * intentionally not exposed to clients.
 */
public record NotificationResponse(
        String id,
        String userId,
        String restaurantId,
        String orderId,
        String title,
        String message,
        String type,
        String channel,
        Boolean read,
        String status,
        Integer attemptCount,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime failedAt,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getRestaurantId(),
                n.getOrderId(),
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.getChannel(),
                n.getRead(),
                n.getStatus(),
                n.getAttemptCount(),
                n.getSentAt(),
                n.getDeliveredAt(),
                n.getFailedAt(),
                n.getCreatedAt());
    }
}
