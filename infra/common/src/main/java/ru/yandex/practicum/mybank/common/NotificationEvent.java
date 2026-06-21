package ru.yandex.practicum.mybank.common;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Structured notification event published to Kafka.
 * Human-readable text is rendered by the consumer from these fields, so producers
 * never duplicate wording. {@code eventId} enables idempotent consumption.
 */
public record NotificationEvent(
        String eventId,
        String login,
        NotificationKind kind,
        BigDecimal amount,
        String currency,
        OffsetDateTime createdAt) {
}
