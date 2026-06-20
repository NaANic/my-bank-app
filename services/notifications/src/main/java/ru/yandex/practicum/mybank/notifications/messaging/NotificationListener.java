package ru.yandex.practicum.mybank.notifications.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mybank.notifications.domain.Notification;
import ru.yandex.practicum.mybank.notifications.domain.NotificationRepository;

/**
 * Consumes notification events from Kafka and persists them.
 * With enable-auto-commit=false and ack-mode=RECORD the offset is committed only
 * after this method returns, so a crash before the commit replays the record
 * (at-least-once). After a restart the consumer group resumes from the last
 * committed offset.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;

    public NotificationListener(NotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${bank.notifications-topic:notifications}",
                   groupId = "${spring.kafka.consumer.group-id:notifications}")
    @Transactional
    public void onMessage(String payload) {
        NotificationMessage event;
        try {
            event = objectMapper.readValue(payload, NotificationMessage.class);
        } catch (JsonProcessingException e) {
            // Malformed payload: log and skip so the offset advances (no poison-message loop).
            log.error("Skipping malformed notification payload: {}", payload, e);
            return;
        }
        Notification persisted = repository.save(
                new Notification(event.login(), event.kind(), event.message()));
        log.info("[notification#{}] login={} kind={} message={}",
                persisted.getId(), persisted.getLogin(), persisted.getKind(), persisted.getMessage());
    }

    record NotificationMessage(String login, String kind, String message) {}
}
