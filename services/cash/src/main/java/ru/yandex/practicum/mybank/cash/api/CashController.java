package ru.yandex.practicum.mybank.cash.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mybank.cash.client.AccountsClient;
import ru.yandex.practicum.mybank.cash.client.NotificationsClient;

@RestController
public class CashController {

    private static final Logger log = LoggerFactory.getLogger(CashController.class);

    private final AccountsClient accounts;
    private final NotificationsClient notifications;

    public CashController(AccountsClient accounts, NotificationsClient notifications) {
        this.accounts = accounts;
        this.notifications = notifications;
    }

    @PostMapping("/deposit")
    public AccountSnapshot deposit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest body) {
        String login = requireLogin(jwt);
        AccountSnapshot snapshot = accounts.credit(login, body.amount());
        notifyBestEffort(login, "cash_deposit",
                "Пополнение наличными: " + login + " внёс " + body.amount() + " ₽");
        return snapshot;
    }

    @PostMapping("/withdraw")
    public AccountSnapshot withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest body) {
        String login = requireLogin(jwt);
        AccountSnapshot snapshot = accounts.debit(login, body.amount());
        notifyBestEffort(login, "cash_withdraw",
                "Снятие наличных: " + login + " снял " + body.amount() + " ₽");
        return snapshot;
    }

    private void notifyBestEffort(String login, String kind, String message) {
        try {
            notifications.send(login, kind, message);
        } catch (Exception e) {
            log.warn("Notification dispatch failed for {} ({}): {}", login, kind, e.toString());
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
