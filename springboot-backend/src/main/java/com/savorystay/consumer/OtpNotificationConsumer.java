package com.savorystay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.config.KafkaTopicConfig;
import com.savorystay.service.ChannelDeliveryService;
import com.savorystay.service.EmailTemplateService;
import com.savorystay.service.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes {@code savorystay.otp} events (otp.generated) and delivers the code
 * over the requested channel:
 *   EMAIL   → branded Gmail HTML template
 *   SMS     → Twilio SMS
 *   WHATSAPP → Twilio WhatsApp
 *
 * In demo mode (no SMTP/Twilio credentials) the code is logged — the API
 * response already surfaces {@code demoOtp} synchronously for local testing.
 *
 * Retries are non-blocking with exponential backoff; exhausted or malformed
 * events dead-letter to {@code savorystay.otp-dlt} and are persisted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OtpNotificationConsumer {

    private final ChannelDeliveryService deliveryService;
    private final EmailTemplateService emailTemplateService;
    private final DltRecorder dltRecorder;
    private final ObjectMapper objectMapper;

    @Value("${app.name}")
    private String appName;

    @RetryableTopic(
            attempts = "4",                                   // 1 initial + 3 retries
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 8000), // 1s → 2s → 4s
            autoCreateTopics = "true",
            exclude = IllegalArgumentException.class,          // permanent errors → DLT immediately
            dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = KafkaTopicConfig.OTP_TOPIC, groupId = "savorystay-otp")
    public void onOtpEvent(String message) {
        KafkaEventPublisher.EventEnvelope envelope = parseEnvelope(message);
        Map<String, Object> p = envelope.payload();

        String channel = KafkaEventMessage.str(p, "channel");
        String target = KafkaEventMessage.str(p, "deliveryTarget");
        String otpCode = KafkaEventMessage.str(p, "otpCode");
        String purpose = KafkaEventMessage.str(p, "purpose");

        if (channel == null || target == null || otpCode == null) {
            throw new IllegalArgumentException("OTP event missing channel/target/code");
        }

        switch (channel) {
            case "EMAIL" -> deliveryService.sendHtmlEmail(target,
                    appName + " - Your OTP for Authentication",
                    emailTemplateService.otpEmail(otpCode, purpose));
            case "SMS" -> deliveryService.sendSms(target,
                    "Your " + appName + " OTP is: " + otpCode + ". Valid for 5 minutes. Do not share this code.");
            case "WHATSAPP" -> deliveryService.sendWhatsApp(target,
                    "Your " + appName + " OTP is: " + otpCode + "\nValid for 5 minutes. Do not share this code.");
            default -> throw new IllegalArgumentException("Unknown OTP channel: " + channel);
        }

        log.info("[KAFKA] OTP delivered via {} to {}", channel, target);
    }

    /** Last stop for exhausted/malformed OTP events — logged + persisted to failed_delivery. */
    @DltHandler
    public void onOtpEventDlt(String message,
                              @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic,
                              @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String error) {
        dltRecorder.record(KafkaTopicConfig.OTP_TOPIC, receivedTopic, message, error);
    }

    /** Throws on malformed JSON so poison messages land in the DLT instead of being silently acked. */
    private KafkaEventPublisher.EventEnvelope parseEnvelope(String message) {
        try {
            return objectMapper.readValue(message, KafkaEventPublisher.EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed OTP event envelope: " + e.getMessage(), e);
        }
    }
}
