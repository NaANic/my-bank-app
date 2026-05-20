package ru.yandex.practicum.mybank.transfer.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountSnapshot(
        String login,
        String firstName,
        String lastName,
        LocalDate dob,
        BigDecimal balance
) {}
