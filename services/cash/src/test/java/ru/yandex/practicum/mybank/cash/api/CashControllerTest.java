package ru.yandex.practicum.mybank.cash.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mybank.cash.AbstractCashIntegrationTest;
import ru.yandex.practicum.mybank.cash.client.AccountsClient;
import ru.yandex.practicum.mybank.cash.client.AccountsServiceException;
import ru.yandex.practicum.mybank.cash.client.NotificationsClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CashControllerTest extends AbstractCashIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountsClient accountsClient;
    @MockitoBean NotificationsClient notificationsClient;

    @Test
    void deposit_requiresAuth() throws Exception {
        mockMvc.perform(post("/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isUnauthorized());
        verify(notificationsClient, never()).send(any(), any(), any());
    }

    @Test
    void deposit_callsAccountsCreditAndNotifies() throws Exception {
        when(accountsClient.credit(eq("alice"), any(BigDecimal.class)))
                .thenReturn(new AccountSnapshot("alice", "Alice", "Andreeva", LocalDate.of(1990, 4, 12), new BigDecimal("1100.00")));

        mockMvc.perform(post("/deposit")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("alice"))
                .andExpect(jsonPath("$.balance").value(1100.00));

        verify(notificationsClient).send(eq("alice"), eq("cash_deposit"), contains("Пополнение"));
    }

    @Test
    void withdraw_callsAccountsDebitAndNotifies() throws Exception {
        when(accountsClient.debit(eq("alice"), any(BigDecimal.class)))
                .thenReturn(new AccountSnapshot("alice", "Alice", "Andreeva", LocalDate.of(1990, 4, 12), new BigDecimal("900.00")));

        mockMvc.perform(post("/withdraw")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk());

        verify(notificationsClient).send(eq("alice"), eq("cash_withdraw"), contains("Снятие"));
    }

    @Test
    void withdraw_propagatesInsufficientFundsAndSkipsNotification() throws Exception {
        when(accountsClient.debit(eq("alice"), any(BigDecimal.class)))
                .thenThrow(new AccountsServiceException(BAD_REQUEST,
                        "{\"error\":\"insufficient_funds\",\"message\":\"not enough\"}"));

        mockMvc.perform(post("/withdraw")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":99999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("insufficient_funds"));

        verify(notificationsClient, never()).send(any(), any(), any());
    }

    @Test
    void deposit_notificationFailureDoesNotFailRequest() throws Exception {
        when(accountsClient.credit(eq("alice"), any(BigDecimal.class)))
                .thenReturn(new AccountSnapshot("alice", "Alice", "Andreeva", LocalDate.of(1990, 4, 12), new BigDecimal("1100.00")));
        doThrow(new RuntimeException("notifications down"))
                .when(notificationsClient).send(any(), any(), any());

        mockMvc.perform(post("/deposit")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1100.00));
    }

    @Test
    void deposit_rejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/deposit")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest());
        verify(notificationsClient, never()).send(any(), any(), any());
    }
}
