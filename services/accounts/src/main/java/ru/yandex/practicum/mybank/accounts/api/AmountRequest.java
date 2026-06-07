package ru.yandex.practicum.mybank.accounts.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AmountRequest(@NotNull @Positive BigDecimal amount) {}
