package ru.yandex.practicum.mybank.accounts.api;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mybank.accounts.domain.Account;
import ru.yandex.practicum.mybank.accounts.domain.AccountRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountDto getOrCreateMe(Jwt jwt) {
        String login = jwt.getClaimAsString("preferred_username");
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("JWT has no preferred_username claim");
        }
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

    @Transactional
    public AccountDto credit(String login, BigDecimal amount) {
        requirePositive(amount);
        Account account = requireExisting(login);
        account.setBalance(account.getBalance().add(amount));
        return toDto(repository.save(account));
    }

    public List<AccountSummary> listOthers(String login) {
        return repository.findAll().stream()
                .filter(a -> !a.getLogin().equals(login))
                .map(a -> new AccountSummary(a.getLogin(), fullName(a)))
                .toList();
    }

    @Transactional
    public AccountDto transfer(String fromLogin, String toLogin, BigDecimal amount) {
        requirePositive(amount);
        if (fromLogin.equals(toLogin)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        Account from = requireExisting(fromLogin);
        Account to = requireExisting(toLogin);
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromLogin, amount, from.getBalance());
        }
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        repository.save(to);
        return toDto(repository.save(from));
    }

    private static String fullName(Account a) {
        String last = a.getLastName() == null ? "" : a.getLastName().trim();
        String first = a.getFirstName() == null ? "" : a.getFirstName().trim();
        if (last.isEmpty() && first.isEmpty()) return a.getLogin();
        if (last.isEmpty()) return first;
        if (first.isEmpty()) return last;
        return last + " " + first;
    }

    @Transactional
    public AccountDto debit(String login, BigDecimal amount) {
        requirePositive(amount);
        Account account = requireExisting(login);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(login, amount, account.getBalance());
        }
        account.setBalance(account.getBalance().subtract(amount));
        return toDto(repository.save(account));
    }

    private Account requireExisting(String login) {
        return repository.findById(login)
                .orElseThrow(() -> new NoSuchElementException("Account '" + login + "' not found"));
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private static AccountDto toDto(Account a) {
        return new AccountDto(a.getLogin(), a.getFirstName(), a.getLastName(), a.getDob(), a.getBalance());
    }
}
