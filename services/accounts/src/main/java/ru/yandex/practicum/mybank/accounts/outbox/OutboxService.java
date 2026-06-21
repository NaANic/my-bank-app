package ru.yandex.practicum.mybank.accounts.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transactional boundary around the outbox. Kept separate from the poller so the
 * claim/markSent/markFailed calls go through the Spring proxy (real transactions).
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;

    public OutboxService(OutboxRepository repository) {
        this.repository = repository;
    }

    /** Claims up to {@code limit} NEW entries and marks them PROCESSING (single transaction). */
    @Transactional
    public List<OutboxEntry> claim(int limit) {
        List<OutboxEntry> batch = repository.findClaimable(limit);
        batch.forEach(OutboxEntry::markProcessing);
        return batch;
    }

    @Transactional
    public void markSent(Long id) {
        repository.findById(id).ifPresent(OutboxEntry::markSent);
    }

    @Transactional
    public void markFailed(Long id) {
        repository.findById(id).ifPresent(OutboxEntry::markFailed);
    }
}
