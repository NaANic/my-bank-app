package ru.yandex.practicum.mybank.accounts.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService service;

    public InternalAccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/{login}/credit")
    public AccountDto credit(@PathVariable String login, @Valid @RequestBody AmountRequest body) {
        return service.credit(login, body.amount());
    }

    @PostMapping("/{login}/debit")
    public AccountDto debit(@PathVariable String login, @Valid @RequestBody AmountRequest body) {
        return service.debit(login, body.amount());
    }

    @PostMapping("/transfers")
    public AccountDto transfer(@Valid @RequestBody TransferOperationRequest body) {
        return service.transfer(body.fromLogin(), body.toLogin(), body.amount());
    }
}
