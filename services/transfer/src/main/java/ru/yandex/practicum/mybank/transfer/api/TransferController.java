package ru.yandex.practicum.mybank.transfer.api;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mybank.common.AccountSnapshot;
import ru.yandex.practicum.mybank.transfer.client.AccountsClient;

@RestController
public class TransferController {

    private final AccountsClient accounts;

    public TransferController(AccountsClient accounts) {
        this.accounts = accounts;
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
        return accounts.transfer(fromLogin, body.toLogin(), body.amount());
    }
}
