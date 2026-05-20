package ru.yandex.practicum.mybank.accounts.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
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
            } catch (Exception e) {
                bumpAttempts(entry.getId());
                log.warn("outbox#{} failed (attempts={}): {}", entry.getId(), entry.getAttempts() + 1, e.toString());
            }
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
    public void bumpAttempts(Long id) {
        repository.findById(id).ifPresent(e -> {
            e.incrementAttempts();
            repository.save(e);
        });
    }
}
