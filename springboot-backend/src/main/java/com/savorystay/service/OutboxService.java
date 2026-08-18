package com.savorystay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savorystay.entity.OutboxEvent;
import com.savorystay.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Records domain events into the transactional outbox.
 * Must be called from within an existing transaction (Propagation.MANDATORY)
 * so the event row commits atomically with the business data change.
 *
 * A scheduled OutboxPoller then reads these rows and dispatches them to
 * downstream services (Notification/SSE, etc.).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(String aggregateId, String eventType, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            json = String.valueOf(payload);
        }
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(json)
                .build();
        return outboxEventRepository.save(event);
    }
}