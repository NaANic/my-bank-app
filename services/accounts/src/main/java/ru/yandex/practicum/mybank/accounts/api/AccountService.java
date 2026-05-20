package ru.yandex.practicum.mybank.accounts.api;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mybank.accounts.domain.Account;
import ru.yandex.practicum.mybank.accounts.domain.AccountRepository;

import java.math.BigDecimal;

@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountDto getOrCreateMe(Jwt jwt) {
        String login = jwt.getClaimAsString("preferred_username");
        Account account = repository.findById(login).orElseGet(() -> {
            Account fresh = new Account(login);
            fresh.setFirstName(jwt.getClaimAsString("given_name"));
            fresh.setLastName(jwt.getClaimAsString("family_name"));
            fresh.setBalance(BigDecimal.ZERO);
            return repository.save(fresh);
        });
        return toDto(account);
    }

    @Transactional
    public AccountDto updateMe(String login, UpdateProfileRequest req) {
        Account account = repository.findById(login).orElseGet(() -> new Account(login));
        account.setFirstName(req.firstName());
        account.setLastName(req.lastName());
        account.setDob(req.dob());
        if (account.getBalance() == null) {
            account.setBalance(BigDecimal.ZERO);
        }
        return toDto(repository.save(account));
    }

    private static AccountDto toDto(Account a) {
        return new AccountDto(a.getLogin(), a.getFirstName(), a.getLastName(), a.getDob(), a.getBalance());
    }
}
