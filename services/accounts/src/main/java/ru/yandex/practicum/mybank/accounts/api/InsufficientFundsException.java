package ru.yandex.practicum.mybank.accounts.api;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String login, BigDecimal requested, BigDecimal available) {
        super("Insufficient funds for '%s': requested %s, available %s"
                .formatted(login, requested, available));
    }
}
