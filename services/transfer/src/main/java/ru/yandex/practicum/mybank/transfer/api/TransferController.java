package ru.yandex.practicum.mybank.transfer.api;

import io.micrometer.core.instrument.MeterRegistry;
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
    private final MeterRegistry meterRegistry;

    public TransferController(AccountsClient accounts, MeterRegistry meterRegistry) {
        this.accounts = accounts;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping("/execute")
    public AccountSnapshot execute(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransferRequest body) {
        String fromLogin = jwt.getClaimAsString("preferred_username");
        if (fromLogin == null || fromLogin.isBlank()) {
            throw new IllegalArgumentException("JWT has no preferred_username claim");
        }
        if (fromLogin.equals(body.toLogin())) {
            countFailure(fromLogin, body.toLogin());
            throw new IllegalArgumentException("Cannot transfer to yourself");
        }
        try {
            return accounts.transfer(fromLogin, body.toLogin(), body.amount());
        } catch (RuntimeException e) {
            countFailure(fromLogin, body.toLogin());
            throw e;
        }
    }

    private void countFailure(String from, String to) {
        meterRegistry.counter("bank.transfer.failed", "from", from, "to", to).increment();
    }
}
