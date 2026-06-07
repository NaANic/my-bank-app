package ru.yandex.practicum.mybank.accounts.api;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mybank.accounts.service.AccountService;

import java.util.List;

@RestController
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public AccountDto me(@AuthenticationPrincipal Jwt jwt) {
        return service.getOrCreateMe(jwt);
    }

    @PutMapping("/me")
    public AccountDto update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req) {
        return service.updateMe(jwt.getClaimAsString("preferred_username"), req);
    }

    @GetMapping("/others")
    public List<AccountSummary> others(@AuthenticationPrincipal Jwt jwt) {
        String login = jwt.getClaimAsString("preferred_username");
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("JWT has no preferred_username claim");
        }
        return service.listOthers(login);
    }
}
