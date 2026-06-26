package ru.yandex.practicum.mybank.accounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mybank.accounts.domain.Account;
import ru.yandex.practicum.mybank.accounts.domain.AccountRepository;
import ru.yandex.practicum.mybank.accounts.outbox.OutboxRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that internal endpoints enforce SCOPE_bank:service.
 * Uses the real security filter chain (no permitAll override).
 */
@AutoConfigureMockMvc
class InternalSecurityIntegrationTest extends AbstractAccountsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accounts;
    @Autowired OutboxRepository outbox;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        accounts.deleteAll();
        Account alice = new Account("alice");
        alice.setFirstName("Alice");
        alice.setLastName("Andreeva");
        alice.setDob(LocalDate.of(1990, 4, 12));
        alice.setBalance(new BigDecimal("1000.00"));
        accounts.save(alice);
    }

    @Test
    void credit_allowedWithServiceScope() throws Exception {
        mockMvc.perform(post("/internal/accounts/alice/credit")
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_bank:service")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1050.00));
    }

    @Test
    void credit_deniedWithoutScope() throws Exception {
        mockMvc.perform(post("/internal/accounts/alice/credit")
                        .with(jwt()) // valid JWT, but no bank:service scope
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void credit_deniedWithoutToken() throws Exception {
        mockMvc.perform(post("/internal/accounts/alice/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50}"))
                .andExpect(status().isUnauthorized());
    }
}
