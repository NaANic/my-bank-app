package ru.yandex.practicum.mybank.accounts.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferOperationRequest(
        @NotBlank String fromLogin,
        @NotBlank String toLogin,
        @NotNull @Positive BigDecimal amount
) {}
