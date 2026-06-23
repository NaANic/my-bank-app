package ru.yandex.practicum.mybank.accounts.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.mybank.common.NotificationEvent;
import ru.yandex.practicum.mybank.common.NotificationKind;
import ru.yandex.practicum.mybank.common.NotificationsClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "bank.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 32;
    private static final String CURRENCY = "RUB";

    private final OutboxService outboxService;
    private final NotificationsClient notifications;
    private final long staleAfterMs;

    public OutboxPoller(OutboxService outboxService,
                        NotificationsClient notifications,
                        @Value("${bank.outbox.stale-after-ms:120000}") long staleAfterMs) {
        this.outboxService = outboxService;
        this.notifications = notifications;
        this.staleAfterMs = staleAfterMs;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval-ms:5000}")
    public void drain() {
        List<OutboxEntry> claimed = outboxService.claim(BATCH_SIZE);
        for (OutboxEntry entry : claimed) {
            try {
                notifications.publish(toEvent(entry));
                outboxService.markSent(entry.getId());
            } catch (Exception e) {
                log.warn("outbox#{} publish failed (attempt {}), retry with backoff: {}",
                        entry.getId(), entry.getAttempts() + 1, e.toString());
                outboxService.markFailed(entry.getId());
            }
        }
    }

    @Scheduled(fixedDelayString = "${bank.outbox.recovery-interval-ms:30000}")
    public void recoverStale() {
        OffsetDateTime threshold = OffsetDateTime.now().minus(Duration.ofMillis(staleAfterMs));
        int reset = outboxService.recoverStale(threshold);
        if (reset > 0) {
            log.warn("recovered {} stale PROCESSING outbox entries (older than {} ms)", reset, staleAfterMs);
        }
    }

    private static NotificationEvent toEvent(OutboxEntry e) {
        return new NotificationEvent(
                e.getEventId().toString(),
                e.getLogin(),
                NotificationKind.valueOf(e.getKind()),
                e.getAmount(),
                CURRENCY,
                e.getCreatedAt());
    }
}
