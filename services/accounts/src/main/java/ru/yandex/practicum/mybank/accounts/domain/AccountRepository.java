package ru.yandex.practicum.mybank.accounts.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}
