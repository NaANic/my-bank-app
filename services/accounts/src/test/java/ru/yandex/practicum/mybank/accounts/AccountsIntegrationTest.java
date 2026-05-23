package ru.yandex.practicum.mybank.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mybank.accounts.api.UpdateProfileRequest;
import ru.yandex.practicum.mybank.accounts.domain.Account;
import ru.yandex.practicum.mybank.accounts.domain.AccountRepository;
import ru.yandex.practicum.mybank.accounts.outbox.OutboxRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountsIntegrationTest extends AbstractAccountsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accounts;
    @Autowired OutboxRepository outbox;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        outbox.deleteAll();
        accounts.deleteAll();
        Account alice = new Account("alice");
        alice.setFirstName("Alice");
        alice.setLastName("Andreeva");
        alice.setDob(LocalDate.of(1990, 4, 12));
        alice.setBalance(new BigDecimal("1000.00"));
        accounts.save(alice);
        Account bob = new Account("bob");
        bob.setFirstName("Bob");
        bob.setLastName("Borisov");
        bob.setDob(LocalDate.of(1985, 9, 25));
        bob.setBalance(new BigDecimal("500.00"));
        accounts.save(bob);
    }

    @Test
    void getMe_requiresAuth() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_returnsCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/me").with(userJwt("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("alice"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    void putMe_updatesProfile() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest("Alisa", "Anderson", LocalDate.of(1991, 1, 2));
        mockMvc.perform(put("/me")
                        .with(userJwt("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alisa"));

        Account saved = accounts.findById("alice").orElseThrow();
        assertThat(saved.getDob()).isEqualTo(LocalDate.of(1991, 1, 2));
    }

    @Test
    void putMe_rejectsUnderageDob() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest("Kid", "Underage", LocalDate.now().minusYears(10));
        mockMvc.perform(put("/me")
                        .with(userJwt("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void others_excludesSelfAndIsAuthenticated() throws Exception {
        mockMvc.perform(get("/others").with(userJwt("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].login").value("bob"));
    }

    @Test
    void internalCredit_requiresServiceScope() throws Exception {
        mockMvc.perform(post("/internal/accounts/alice/credit")
                        .with(userJwt("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalCredit_succeedsWithServiceScopeAndWritesOutbox() throws Exception {
        mockMvc.perform(post("/internal/accounts/alice/credit")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1050.00));

        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getKind()).isEqualTo("balance_credit");
    }

    @Test
    void internalDebit_rejectsInsufficientFunds() throws Exception {
        mockMvc.perform(post("/internal/accounts/alice/debit")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":99999.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("insufficient_funds"));
    }

    @Test
    void internalTransfer_movesBalancesAtomically() throws Exception {
        Map<String, Object> body = Map.of("fromLogin", "alice", "toLogin", "bob", "amount", new BigDecimal("100.00"));
        mockMvc.perform(post("/internal/accounts/transfers")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(900.00));

        assertThat(accounts.findById("bob").orElseThrow().getBalance()).isEqualByComparingTo("600.00");
        assertThat(outbox.count()).isEqualTo(2);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor userJwt(String login) {
        return jwt().jwt(b -> b
                .claim("preferred_username", login)
                .claim("scope", "profile email"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor serviceJwt() {
        return jwt().jwt(b -> b.claim("scope", "bank:service"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_bank:service"));
    }
}
