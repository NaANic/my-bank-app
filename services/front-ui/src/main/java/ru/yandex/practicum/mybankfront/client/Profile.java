package ru.yandex.practicum.mybankfront.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Profile(
        String login,
        String firstName,
        String lastName,
        LocalDate dob,
        BigDecimal balance
) {}
