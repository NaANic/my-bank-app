package ru.yandex.practicum.mybank.cash.api;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mybank.cash.client.AccountsClient;
import ru.yandex.practicum.mybank.common.AccountSnapshot;

@RestController
public class CashController {

    private final AccountsClient accounts;
    private final MeterRegistry meterRegistry;

    public CashController(AccountsClient accounts, MeterRegistry meterRegistry) {
        this.accounts = accounts;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping("/deposit")
    public AccountSnapshot deposit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest body) {
        return accounts.credit(requireLogin(jwt), body.amount());
    }

    @PostMapping("/withdraw")
    public AccountSnapshot withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest body) {
        String login = requireLogin(jwt);
        try {
            return accounts.debit(login, body.amount());
        } catch (RuntimeException e) {
            meterRegistry.counter("bank.cash.withdraw.failed", "login", login).increment();
            throw e;
        }
    }

    private static String requireLogin(Jwt jwt) {
        String login = jwt.getClaimAsString("preferred_username");
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("JWT has no preferred_username claim");
        }
        return login;
    }
}
