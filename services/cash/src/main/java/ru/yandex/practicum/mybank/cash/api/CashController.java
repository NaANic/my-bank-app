package ru.yandex.practicum.mybank.cash.api;

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

    public CashController(AccountsClient accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/deposit")
    public AccountSnapshot deposit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest body) {
        return accounts.credit(requireLogin(jwt), body.amount());
    }

    @PostMapping("/withdraw")
    public AccountSnapshot withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest body) {
        return accounts.debit(requireLogin(jwt), body.amount());
    }

    private static String requireLogin(Jwt jwt) {
        String login = jwt.getClaimAsString("preferred_username");
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("JWT has no preferred_username claim");
        }
        return login;
    }
}
