package com.savorystay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.config.KafkaTopicConfig;
import com.savorystay.service.ChannelDeliveryService;
import com.savorystay.service.EmailTemplateService;
import com.savorystay.service.KafkaEventPublisher;
import com.savorystay.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Consumes {@code savorystay.payments} events (payment.confirmed).
 *
 * Notifies the customer in-app (SSE) and emails them a branded Gmail receipt
 * with the order number, amount and payment method.
 *
 * Retries are non-blocking with exponential backoff; exhausted or malformed
 * events dead-letter to {@code savorystay.payments-dlt} and are persisted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentNotificationConsumer {

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
    @KafkaListener(topics = KafkaTopicConfig.PAYMENTS_TOPIC, groupId = "savorystay-payments")
    public void onPaymentEvent(String message) {
        KafkaEventPublisher.EventEnvelope envelope = parseEnvelope(message);
        Map<String, Object> p = envelope.payload();

        String orderId = KafkaEventMessage.str(p, "orderId");
        String restaurantId = KafkaEventMessage.str(p, "restaurantId");
        String orderNumber = KafkaEventMessage.str(p, "orderNumber");
        String customerName = KafkaEventMessage.str(p, "customerName");
        String userId = KafkaEventMessage.str(p, "userId");
        String deliveryEmail = KafkaEventMessage.str(p, "customerEmail");
        String gateway = KafkaEventMessage.str(p, "gateway");
        BigDecimal amount = KafkaEventMessage.num(p, "totalAmount");

        // Branded Gmail receipt FIRST — the email is the most likely transient
        // failure; retrying before any notification side-effects avoids duplicates.
        if (deliveryEmail != null && !deliveryEmail.isBlank()) {
            deliveryService.sendHtmlEmail(deliveryEmail, "Payment receipt — " + orderNumber,
                    emailTemplateService.receiptEmail(orderNumber, amount, gateway, customerName));
        }

        // In-app notification (SSE) to the customer
        if (userId != null) {
            notificationService.create(userId, restaurantId, orderId,
                    "Payment received ✅ " + orderNumber,
                    "Your payment of ₹" + amount + " via " + gateway + " for " + orderNumber
                            + " was successful.",
                    "PAYMENT_RECEIPT", "APP");
        }
    }

    /** Last stop for exhausted/malformed payment events — logged + persisted to failed_delivery. */
    @DltHandler
    public void onPaymentEventDlt(String message,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic,
                                  @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String error) {
        dltRecorder.record(KafkaTopicConfig.PAYMENTS_TOPIC, receivedTopic, message, error);
    }

    /** Throws on malformed JSON so poison messages land in the DLT instead of being silently acked. */
    private KafkaEventPublisher.EventEnvelope parseEnvelope(String message) {
        try {
            return objectMapper.readValue(message, KafkaEventPublisher.EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed payment event envelope: " + e.getMessage(), e);
        }
    }
}
