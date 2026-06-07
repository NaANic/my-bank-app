package ru.yandex.practicum.mybank.accounts.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    List<OutboxEntry> findBySentAtIsNullOrderByIdAsc(Pageable pageable);
}
