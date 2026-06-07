package ru.yandex.practicum.mybank.transfer.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mybank.transfer.client.AccountsClient;
import ru.yandex.practicum.mybank.transfer.client.NotificationsClient;

@RestController
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final AccountsClient accounts;
    private final NotificationsClient notifications;

    public TransferController(AccountsClient accounts, NotificationsClient notifications) {
        this.accounts = accounts;
        this.notifications = notifications;
    }

    @PostMapping("/execute")
    public AccountSnapshot execute(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransferRequest body) {
        String fromLogin = jwt.getClaimAsString("preferred_username");
        if (fromLogin == null || fromLogin.isBlank()) {
            throw new IllegalArgumentException("JWT has no preferred_username claim");
        }
        if (fromLogin.equals(body.toLogin())) {
            throw new IllegalArgumentException("Cannot transfer to yourself");
        }
        AccountSnapshot snapshot = accounts.transfer(fromLogin, body.toLogin(), body.amount());
        notifyBestEffort(fromLogin, "transfer",
                "Перевод: " + fromLogin + " → " + body.toLogin() + ", " + body.amount() + " ₽");
        return snapshot;
    }

    private void notifyBestEffort(String login, String kind, String message) {
        try {
            notifications.send(login, kind, message);
        } catch (Exception e) {
            log.warn("Notification dispatch failed for {} ({}): {}", login, kind, e.toString());
        }
    }
}
