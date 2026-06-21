package ru.yandex.practicum.mybank.accounts.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.mybank.common.NotificationEvent;
import ru.yandex.practicum.mybank.common.NotificationKind;
import ru.yandex.practicum.mybank.common.NotificationsClient;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "bank.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 32;
    private static final String CURRENCY = "RUB";

    private final OutboxService outboxService;
    private final NotificationsClient notifications;

    public OutboxPoller(OutboxService outboxService, NotificationsClient notifications) {
        this.outboxService = outboxService;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval-ms:5000}")
    public void drain() {
        List<OutboxEntry> claimed = outboxService.claim(BATCH_SIZE);
        for (OutboxEntry entry : claimed) {
            try {
                notifications.publish(toEvent(entry));
                outboxService.markSent(entry.getId());
            } catch (Exception e) {
                log.warn("outbox#{} publish failed, will retry: {}", entry.getId(), e.toString());
                outboxService.markFailed(entry.getId());
            }
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
