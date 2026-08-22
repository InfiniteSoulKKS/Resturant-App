package com.savorystay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.config.KafkaTopicConfig;
import com.savorystay.repository.RestaurantRepository;
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
 * Consumes {@code savorystay.inventory} events (inventory.stock.low).
 *
 * Alerts restaurant staff over SSE and emails the restaurant's registered
 * contact address a branded low-stock alert via Gmail.
 *
 * Retries are non-blocking with exponential backoff; exhausted or malformed
 * events dead-letter to {@code savorystay.inventory-dlt} and are persisted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryNotificationConsumer {

    private final NotificationService notificationService;
    private final ChannelDeliveryService deliveryService;
    private final EmailTemplateService emailTemplateService;
    private final RestaurantRepository restaurantRepository;
    private final DltRecorder dltRecorder;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "4",                                   // 1 initial + 3 retries
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 8000), // 1s → 2s → 4s
            autoCreateTopics = "true",
            exclude = IllegalArgumentException.class,          // permanent errors → DLT immediately
            dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_TOPIC, groupId = "savorystay-inventory")
    public void onInventoryEvent(String message) {
        KafkaEventPublisher.EventEnvelope envelope = parseEnvelope(message);
        Map<String, Object> p = envelope.payload();

        String restaurantId = KafkaEventMessage.str(p, "restaurantId");
        String ingredientId = KafkaEventMessage.str(p, "ingredientId");
        String ingredientName = KafkaEventMessage.str(p, "name");
        BigDecimal stock = KafkaEventMessage.num(p, "stockQuantity");
        BigDecimal reorderLevel = KafkaEventMessage.num(p, "reorderLevel");

        // Gmail alert FIRST — the email is the most likely transient failure;
        // retrying before any notification side-effects avoids duplicates.
        if (restaurantId != null) {
            restaurantRepository.findById(restaurantId).ifPresent(restaurant -> {
                if (restaurant.getEmail() != null && !restaurant.getEmail().isBlank()) {
                    deliveryService.sendHtmlEmail(restaurant.getEmail(),
                            "⚠️ Low stock: " + ingredientName,
                            emailTemplateService.inventoryAlertEmail(ingredientName, stock, reorderLevel));
                }
            });
        }

        // Staff in-app alert (SSE to the restaurant's kitchen/management boards)
        notificationService.create(null, restaurantId, ingredientId,
                "⚠️ Low stock: " + ingredientName,
                "Stock for " + ingredientName + " is " + stock + " (reorder level " + reorderLevel
                        + "). Please restock.",
                "INVENTORY_ALERT", "APP");
    }

    /** Last stop for exhausted/malformed inventory events — logged + persisted to failed_delivery. */
    @DltHandler
    public void onInventoryEventDlt(String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic,
                                    @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String error) {
        dltRecorder.record(KafkaTopicConfig.INVENTORY_TOPIC, receivedTopic, message, error);
    }

    /** Throws on malformed JSON so poison messages land in the DLT instead of being silently acked. */
    private KafkaEventPublisher.EventEnvelope parseEnvelope(String message) {
        try {
            return objectMapper.readValue(message, KafkaEventPublisher.EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed inventory event envelope: " + e.getMessage(), e);
        }
    }
}
