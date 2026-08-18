package com.savorystay.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topics for the event-driven notification pipeline.
 *
 * The transactional outbox ({@code outbox_event}) is drained by
 * {@link com.savorystay.scheduler.OutboxPoller}, which publishes each event to
 * one of these topics. Dedicated consumers then dispatch real-time
 * notifications (Gmail emails, Twilio SMS/WhatsApp, in-app SSE pushes).
 */
@Configuration
public class KafkaTopicConfig {

    public static final String ORDERS_TOPIC = "savorystay.orders";
    public static final String OTP_TOPIC = "savorystay.otp";
    public static final String INVENTORY_TOPIC = "savorystay.inventory";
    public static final String PAYMENTS_TOPIC = "savorystay.payments";

    /** Topics are created lazily by KafkaAdmin on startup (silently skipped when the broker is down). */
    @Bean
    public NewTopic ordersTopic() {
        return topic(ORDERS_TOPIC);
    }

    @Bean
    public NewTopic otpTopic() {
        return topic(OTP_TOPIC);
    }

    @Bean
    public NewTopic inventoryTopic() {
        return topic(INVENTORY_TOPIC);
    }

    @Bean
    public NewTopic paymentsTopic() {
        return topic(PAYMENTS_TOPIC);
    }

    private NewTopic topic(String name) {
        // 3 partitions per topic; RF 1 fits the single-node local broker.
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
