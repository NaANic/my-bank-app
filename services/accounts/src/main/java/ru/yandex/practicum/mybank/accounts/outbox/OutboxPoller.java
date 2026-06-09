package ru.yandex.practicum.mybank.accounts.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mybank.common.NotificationsClient;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "bank.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 32;

    private final OutboxRepository repository;
    private final NotificationsClient notifications;

    public OutboxPoller(OutboxRepository repository, NotificationsClient notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval-ms:5000}")
    public void drain() {
        List<OutboxEntry> pending = repository.findBySentAtIsNullOrderByIdAsc(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEntry entry : pending) {
            try {
                notifications.send(entry.getLogin(), entry.getKind(), entry.getMessage());
                markSent(entry.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("outbox#{} already processed by another instance, skipping", entry.getId());
            } catch (Exception e) {
                recordFailure(entry.getId(), e);
            }
        }
    }

    private void recordFailure(Long id, Exception cause) {
        try {
            int attempts = bumpAttempts(id);
            log.warn("outbox#{} failed (attempts={}): {}", id, attempts, cause.toString());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("outbox#{} concurrently modified while recording failure, skipping", id);
        }
    }

    @Transactional
    public void markSent(Long id) {
        repository.findById(id).ifPresent(e -> {
            e.markSent();
            repository.save(e);
        });
    }

    @Transactional
    public int bumpAttempts(Long id) {
        return repository.findById(id).map(e -> {
            e.incrementAttempts();
            return repository.save(e).getAttempts();
        }).orElse(0);
    }
}
