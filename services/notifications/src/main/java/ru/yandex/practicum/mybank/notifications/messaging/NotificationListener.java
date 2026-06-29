package ru.yandex.practicum.mybank.notifications.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mybank.notifications.domain.Notification;
import ru.yandex.practicum.mybank.notifications.domain.NotificationRepository;

import java.math.BigDecimal;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public NotificationListener(NotificationRepository repository,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "${bank.notifications-topic:notifications}",
                   groupId = "${spring.kafka.consumer.group-id:notifications}")
    @Transactional
    public void onMessage(String payload) {
        NotificationEvent event;
        try {
            event = objectMapper.readValue(payload, NotificationEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping malformed notification payload: {}", payload, e);
            return;
        }

        if (event.eventId() == null || event.eventId().isBlank()) {
            log.error("Skipping notification with missing eventId: {}", payload);
            return;
        }
        if (event.login() == null || event.login().isBlank()) {
            log.error("Skipping notification with missing login: {}", payload);
            return;
        }
        if (event.kind() == null || event.kind().isBlank()) {
            log.error("Skipping notification with missing kind: {}", payload);
            return;
        }

        if (repository.existsByEventId(event.eventId())) {
            log.info("Duplicate notification eventId={} — skipped", event.eventId());
            return;
        }

        String message = renderMessage(event);
        try {
            Notification saved = repository.save(
                    new Notification(event.eventId(), event.login(), event.kind(), message));
            log.info("[notification#{}] login={} kind={} message={}",
                    saved.getId(), saved.getLogin(), saved.getKind(), saved.getMessage());
        } catch (RuntimeException e) {
            meterRegistry.counter("bank.notification.failed", "login", event.login()).increment();
            log.error("Failed to store notification eventId={} login={}", event.eventId(), event.login(), e);
            throw e;
        }
    }

    private static String renderMessage(NotificationEvent e) {
        BigDecimal amount = e.amount();
        String currency = e.currency() != null ? e.currency() : "RUB";
        String amountStr = amount != null ? amount.toPlainString() + " " + currency : "—";
        return switch (e.kind()) {
            case "BALANCE_CREDIT"       -> "Пополнение наличными: " + e.login() + " внёс " + amountStr;
            case "BALANCE_DEBIT"        -> "Снятие наличных: " + e.login() + " снял " + amountStr;
            case "BALANCE_TRANSFER_OUT" -> "Исходящий перевод: " + e.login() + " отправил " + amountStr;
            case "BALANCE_TRANSFER_IN"  -> "Входящий перевод: " + e.login() + " получил " + amountStr;
            default                     -> "Уведомление (" + e.kind() + "): " + amountStr;
        };
    }

    record NotificationEvent(
            String eventId,
            String login,
            String kind,
            BigDecimal amount,
            String currency,
            String createdAt) {}
}
