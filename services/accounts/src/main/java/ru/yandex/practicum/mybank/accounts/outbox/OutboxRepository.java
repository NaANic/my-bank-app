package ru.yandex.practicum.mybank.accounts.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    /**
     * Atomically selects claimable entries for the current transaction.
     * FOR UPDATE SKIP LOCKED lets multiple replicas drain the outbox without
     * grabbing the same rows; next_attempt_at enforces retry backoff.
     * The table is schema-qualified because native queries ignore Hibernate's
     * default_schema and the connection search_path does not include it.
     */
    @Query(value = "select * from accounts.outbox where status = 'NEW' "
            + "and (next_attempt_at is null or next_attempt_at <= now()) "
            + "order by id for update skip locked limit :limit", nativeQuery = true)
    List<OutboxEntry> findClaimable(@Param("limit") int limit);

    /**
     * Returns PROCESSING entries that got stuck (the app crashed between claim and
     * markSent/markFailed) back to NEW so they can be retried. Re-sending is safe
     * because the consumer is idempotent by eventId.
     */
    @Modifying
    @Query(value = "update accounts.outbox set status = 'NEW', processing_at = null "
            + "where status = 'PROCESSING' and processing_at < :threshold", nativeQuery = true)
    int resetStale(@Param("threshold") OffsetDateTime threshold);
}
