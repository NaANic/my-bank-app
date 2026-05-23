package ru.yandex.practicum.mybank.accounts.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountDto(
        String login,
        String firstName,
        String lastName,
        LocalDate dob,
        BigDecimal balance
) {}
