package ru.yandex.practicum.mybank.accounts.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.yandex.practicum.mybank.accounts.domain.Account;
import ru.yandex.practicum.mybank.accounts.domain.AccountRepository;
import ru.yandex.practicum.mybank.accounts.outbox.OutboxEntry;
import ru.yandex.practicum.mybank.accounts.outbox.OutboxRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository repository;
    @Mock OutboxRepository outbox;

    AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(repository, outbox);
    }

    @Test
    void getOrCreateMe_returnsExisting() {
        Account existing = account("alice", "Alice", "Andreeva", LocalDate.of(1990, 4, 12), new BigDecimal("100.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(existing));

        AccountDto dto = service.getOrCreateMe(jwt("alice", "Alice", "Andreeva"));

        assertThat(dto.login()).isEqualTo("alice");
        assertThat(dto.balance()).isEqualByComparingTo("100.00");
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreateMe_createsFromJwtClaims() {
        when(repository.findById("carol")).thenReturn(Optional.empty());
        when(repository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountDto dto = service.getOrCreateMe(jwt("carol", "Carol", "Smith"));

        assertThat(dto.firstName()).isEqualTo("Carol");
        assertThat(dto.lastName()).isEqualTo("Smith");
        assertThat(dto.balance()).isEqualByComparingTo("0");
    }

    @Test
    void getOrCreateMe_rejectsMissingPreferredUsername() {
        assertThatThrownBy(() -> service.getOrCreateMe(jwt(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preferred_username");
    }

    @Test
    void updateMe_rejectsAgeUnder18() {
        UpdateProfileRequest req = new UpdateProfileRequest("Teen", "Ager", LocalDate.now().minusYears(10));

        assertThatThrownBy(() -> service.updateMe("alice", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18");
        verify(repository, never()).save(any());
    }

    @Test
    void updateMe_savesChanges() {
        Account existing = account("alice", "Old", "Name", LocalDate.of(1990, 4, 12), new BigDecimal("100.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(existing));
        when(repository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateProfileRequest req = new UpdateProfileRequest("New", "Name", LocalDate.of(1995, 1, 1));

        AccountDto dto = service.updateMe("alice", req);

        assertThat(dto.firstName()).isEqualTo("New");
        assertThat(dto.dob()).isEqualTo(LocalDate.of(1995, 1, 1));
        assertThat(dto.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void credit_writesOutboxRow() {
        Account existing = account("alice", "Alice", "A", LocalDate.of(1990, 4, 12), new BigDecimal("100.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(existing));
        when(repository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountDto dto = service.credit("alice", new BigDecimal("50.00"));

        assertThat(dto.balance()).isEqualByComparingTo("150.00");
        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outbox).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("balance_credit");
        assertThat(captor.getValue().getLogin()).isEqualTo("alice");
    }

    @Test
    void debit_throwsOnInsufficientFunds() {
        Account existing = account("alice", "Alice", "A", LocalDate.of(1990, 4, 12), new BigDecimal("10.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.debit("alice", new BigDecimal("50.00")))
                .isInstanceOf(InsufficientFundsException.class);
        verify(repository, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void debit_writesOutboxRow() {
        Account existing = account("alice", "Alice", "A", LocalDate.of(1990, 4, 12), new BigDecimal("100.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(existing));
        when(repository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.debit("alice", new BigDecimal("30.00"));

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outbox).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("balance_debit");
    }

    @Test
    void transfer_movesBalancesAtomically() {
        Account from = account("alice", "Alice", "A", LocalDate.of(1990, 4, 12), new BigDecimal("100.00"));
        Account to = account("bob", "Bob", "B", LocalDate.of(1990, 1, 1), new BigDecimal("50.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(from));
        when(repository.findById("bob")).thenReturn(Optional.of(to));
        when(repository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountDto dto = service.transfer("alice", "bob", new BigDecimal("40.00"));

        assertThat(dto.balance()).isEqualByComparingTo("60.00");
        assertThat(to.getBalance()).isEqualByComparingTo("90.00");
        verify(outbox, org.mockito.Mockito.times(2)).save(any(OutboxEntry.class));
    }

    @Test
    void transfer_rejectsInsufficientFunds() {
        Account from = account("alice", "Alice", "A", LocalDate.of(1990, 4, 12), new BigDecimal("10.00"));
        Account to = account("bob", "Bob", "B", LocalDate.of(1990, 1, 1), new BigDecimal("50.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(from));
        when(repository.findById("bob")).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.transfer("alice", "bob", new BigDecimal("100.00")))
                .isInstanceOf(InsufficientFundsException.class);
        verify(outbox, never()).save(any());
    }

    @Test
    void transfer_rejectsMissingRecipient() {
        Account from = account("alice", "Alice", "A", LocalDate.of(1990, 4, 12), new BigDecimal("100.00"));
        when(repository.findById("alice")).thenReturn(Optional.of(from));
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer("alice", "ghost", new BigDecimal("10")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void transfer_rejectsSameAccount() {
        assertThatThrownBy(() -> service.transfer("alice", "alice", new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listOthers_excludesSelf() {
        Account alice = account("alice", "Alice", "A", LocalDate.of(1990, 4, 12), BigDecimal.ZERO);
        Account bob = account("bob", "Bob", "B", LocalDate.of(1990, 1, 1), BigDecimal.ZERO);
        when(repository.findAll()).thenReturn(List.of(alice, bob));

        List<AccountSummary> others = service.listOthers("alice");

        assertThat(others).hasSize(1);
        assertThat(others.get(0).login()).isEqualTo("bob");
        assertThat(others.get(0).name()).isEqualTo("B Bob");
    }

    private static Account account(String login, String first, String last, LocalDate dob, BigDecimal balance) {
        Account a = new Account(login);
        a.setFirstName(first);
        a.setLastName(last);
        a.setDob(dob);
        a.setBalance(balance);
        return a;
    }

    private static Jwt jwt(String preferredUsername, String givenName, String familyName) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (preferredUsername != null) b.claim("preferred_username", preferredUsername);
        if (givenName != null) b.claim("given_name", givenName);
        if (familyName != null) b.claim("family_name", familyName);
        return b.build();
    }
}
