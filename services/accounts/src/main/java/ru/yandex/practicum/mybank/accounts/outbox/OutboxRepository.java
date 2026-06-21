package ru.yandex.practicum.mybank.accounts.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    /**
     * Atomically selects pending entries for the current transaction.
     * FOR UPDATE SKIP LOCKED lets multiple replicas drain the outbox without
     * grabbing the same rows, preventing double publication.
     */
    @Query(value = "select * from outbox where status = 'NEW' order by id "
            + "for update skip locked limit :limit", nativeQuery = true)
    List<OutboxEntry> findClaimable(@Param("limit") int limit);
}
