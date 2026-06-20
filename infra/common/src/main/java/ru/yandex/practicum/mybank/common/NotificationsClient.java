package ru.yandex.practicum.mybank.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Sends notification events to Kafka (JSON value, login as key).
 * Blocks on the broker ack (acks=all) to provide at-least-once delivery:
 * a failed send surfaces as an exception so the caller can retry or skip.
 */
public class NotificationsClient {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public NotificationsClient(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper,
                               String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void send(String login, String kind, String message) {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("login", login);
        event.put("kind", kind);
        event.put("message", message);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification event", e);
        }

        try {
            kafkaTemplate.send(topic, login, payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending notification", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to send notification to Kafka", e.getCause());
        }
    }
}
