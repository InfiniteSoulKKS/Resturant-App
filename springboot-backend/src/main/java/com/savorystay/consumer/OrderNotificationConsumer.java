package com.savorystay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.config.KafkaTopicConfig;
import com.savorystay.service.ChannelDeliveryService;
import com.savorystay.service.EmailTemplateService;
import com.savorystay.service.KafkaEventPublisher;
import com.savorystay.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consumes {@code savorystay.orders} events (order.created, order.status.changed).
 *
 * Delivers in real time to all users:
 *  - in-app SSE push + SMS/WhatsApp via {@link NotificationService},
 *  - a rich, branded Gmail (HTML) email built from the order data.
 *
 * Retries are non-blocking: transient delivery failures (SMTP/Twilio/DB) retry
 * on {@code savorystay.orders-retry-*} topics with exponential backoff, then
 * dead-letter to {@code savorystay.orders-dlt}. Malformed envelopes are
 * permanent failures and are routed straight to the DLT (no retries).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationConsumer {

    private final NotificationService notificationService;
    private final ChannelDeliveryService deliveryService;
    private final EmailTemplateService emailTemplateService;
    private final DltRecorder dltRecorder;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "4",                                   // 1 initial + 3 retries
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 8000), // 1s → 2s → 4s
            autoCreateTopics = "true",
            exclude = IllegalArgumentException.class,          // permanent errors → DLT immediately
            dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = KafkaTopicConfig.ORDERS_TOPIC, groupId = "savorystay-orders")
    public void onOrderEvent(String message) {
        KafkaEventPublisher.EventEnvelope envelope = parseEnvelope(message);
        switch (envelope.eventType()) {
            case "order.created" -> handleOrderCreated(envelope.payload());
            case "order.status.changed" -> handleOrderStatusChanged(envelope.payload());
            default -> throw new IllegalArgumentException("Unknown order event type: " + envelope.eventType());
        }
    }

    /** Last stop for exhausted/malformed events — logged + persisted to failed_delivery. */
    @DltHandler
    public void onOrderEventDlt(String message,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic,
                                @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String error) {
        dltRecorder.record(KafkaTopicConfig.ORDERS_TOPIC, receivedTopic, message, error);
    }

    private void handleOrderCreated(Map<String, Object> p) {
        String restaurantId = KafkaEventMessage.str(p, "restaurantId");
        String orderId = KafkaEventMessage.str(p, "orderId");
        String orderNumber = KafkaEventMessage.str(p, "orderNumber");
        String customerName = KafkaEventMessage.str(p, "customerName");
        String userId = KafkaEventMessage.str(p, "userId");
        String orderType = KafkaEventMessage.str(p, "orderType");
        String deliveryPhone = KafkaEventMessage.str(p, "customerPhone");
        String deliveryEmail = KafkaEventMessage.str(p, "customerEmail");
        BigDecimal total = KafkaEventMessage.num(p, "totalAmount");

        // Rich Gmail confirmation FIRST — the email is the most likely transient
        // failure, so on retry nothing has been persisted yet (no duplicate
        // notification rows, SMS or WhatsApp pushes).
        if (deliveryEmail != null && !deliveryEmail.isBlank()) {
            deliveryService.sendHtmlEmail(deliveryEmail, "Order confirmed ✅ " + orderNumber,
                    emailTemplateService.orderEmail(orderNumber, total, orderType, "confirmed", customerName));
        }

        // Customer confirmation — SSE + SMS / WhatsApp in real time
        if (userId != null) {
            notificationService.create(userId, restaurantId, orderId,
                    "Order confirmed ✅ " + orderNumber,
                    "Thanks " + customerName + "! Your " + orderType
                            + " order for ₹" + total + " is confirmed and will be prepared soon.",
                    "ORDER_CONFIRMED", "APP,SMS,WHATSAPP", deliveryPhone, deliveryEmail);
        }

        // Restaurant staff broadcast (in-app SSE)
        notificationService.create(null, restaurantId, orderId,
                "New Order " + orderNumber,
                "New " + orderType + " order from " + customerName + " for ₹" + total,
                "NEW_ORDER", "APP");
    }

    private void handleOrderStatusChanged(Map<String, Object> p) {
        String userId = KafkaEventMessage.str(p, "userId");
        String restaurantId = KafkaEventMessage.str(p, "restaurantId");
        String orderId = KafkaEventMessage.str(p, "orderId");
        String orderNumber = KafkaEventMessage.str(p, "orderNumber");
        String customerName = KafkaEventMessage.str(p, "customerName");
        String orderType = KafkaEventMessage.str(p, "orderType");
        String newStatus = KafkaEventMessage.str(p, "status");
        BigDecimal total = KafkaEventMessage.num(p, "totalAmount");
        String deliveryPhone = KafkaEventMessage.str(p, "customerPhone");
        String deliveryEmail = KafkaEventMessage.str(p, "customerEmail");

        if (userId != null) {
            if ("PACKED_READY".equals(newStatus)) {
                String pickupMsg = "DINE_IN".equalsIgnoreCase(orderType)
                        ? "Your table will be served shortly!"
                        : "Please collect it from the restaurant.";
                // Email first — see handleOrderCreated for the retry-duplication rationale.
                if (deliveryEmail != null && !deliveryEmail.isBlank()) {
                    deliveryService.sendHtmlEmail(deliveryEmail, "Your order is ready! 🎉 " + orderNumber,
                            emailTemplateService.orderReadyEmail(orderNumber, orderType, customerName));
                }
                notificationService.create(userId, restaurantId, orderId,
                        "Your order is ready! 🎉 " + orderNumber,
                        "Great news " + customerName + "! Your order is packed and ready. " + pickupMsg,
                        "ORDER_READY", "APP,SMS,WHATSAPP", deliveryPhone, deliveryEmail);
            } else {
                // Every status move (NEW → PREPARING → COMPLETED …) alerts the
                // customer in real time on all channels.
                String label = KafkaEventMessage.humanize(newStatus);
                // Email first — see handleOrderCreated for the retry-duplication rationale.
                if (deliveryEmail != null && !deliveryEmail.isBlank()) {
                    deliveryService.sendHtmlEmail(deliveryEmail, "Order " + orderNumber + " — " + label,
                            emailTemplateService.orderEmail(orderNumber, total, orderType, label, customerName));
                }
                notificationService.create(userId, restaurantId, orderId,
                        "Order " + orderNumber + " is " + label,
                        "Your order " + orderNumber + " status changed to " + label + ".",
                        "ORDER_STATUS", "APP,SMS,WHATSAPP", deliveryPhone, deliveryEmail);
            }
        }

        // Restaurant staff broadcast (in-app SSE)
        String label = KafkaEventMessage.humanize(newStatus);
        notificationService.create(null, restaurantId, orderId,
                "Order " + orderNumber + " → " + label,
                "Order " + orderNumber + " has moved to " + label + ".",
                "ORDER_STATUS", "APP");
    }

    /** Throws on malformed JSON so poison messages land in the DLT instead of being silently acked. */
    private KafkaEventPublisher.EventEnvelope parseEnvelope(String message) {
        try {
            return objectMapper.readValue(message, KafkaEventPublisher.EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed order event envelope: " + e.getMessage(), e);
        }
    }
}
