package ru.yandex.practicum.mybank.transfer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mybank.transfer.AbstractTransferIntegrationTest;
import ru.yandex.practicum.mybank.transfer.client.AccountsClient;
import ru.yandex.practicum.mybank.transfer.client.AccountsServiceException;
import ru.yandex.practicum.mybank.transfer.client.NotificationsClient;

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
class TransferControllerTest extends AbstractTransferIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountsClient accountsClient;
    @MockitoBean NotificationsClient notificationsClient;

    @Test
    void execute_requiresAuth() throws Exception {
        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toLogin\":\"bob\",\"amount\":10}"))
                .andExpect(status().isUnauthorized());
        verify(notificationsClient, never()).send(any(), any(), any());
    }

    @Test
    void execute_callsAccountsAndNotifies() throws Exception {
        when(accountsClient.transfer(eq("alice"), eq("bob"), any(BigDecimal.class)))
                .thenReturn(new AccountSnapshot("alice", "Alice", "Andreeva", LocalDate.of(1990, 4, 12), new BigDecimal("950.00")));

        mockMvc.perform(post("/execute")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toLogin\":\"bob\",\"amount\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(950.00));

        verify(notificationsClient).send(eq("alice"), eq("transfer"), contains("alice → bob"));
    }

    @Test
    void execute_rejectsSelfTransferAndSkipsNotification() throws Exception {
        mockMvc.perform(post("/execute")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toLogin\":\"alice\",\"amount\":10}"))
                .andExpect(status().isBadRequest());

        verify(notificationsClient, never()).send(any(), any(), any());
    }

    @Test
    void execute_propagatesInsufficientFundsAndSkipsNotification() throws Exception {
        when(accountsClient.transfer(eq("alice"), eq("bob"), any(BigDecimal.class)))
                .thenThrow(new AccountsServiceException(BAD_REQUEST,
                        "{\"error\":\"insufficient_funds\",\"message\":\"alice\"}"));

        mockMvc.perform(post("/execute")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toLogin\":\"bob\",\"amount\":9999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("insufficient_funds"));

        verify(notificationsClient, never()).send(any(), any(), any());
    }

    @Test
    void execute_notificationFailureDoesNotFailRequest() throws Exception {
        when(accountsClient.transfer(eq("alice"), eq("bob"), any(BigDecimal.class)))
                .thenReturn(new AccountSnapshot("alice", "Alice", "Andreeva", LocalDate.of(1990, 4, 12), new BigDecimal("950.00")));
        doThrow(new RuntimeException("notifications down"))
                .when(notificationsClient).send(any(), any(), any());

        mockMvc.perform(post("/execute")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toLogin\":\"bob\",\"amount\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(950.00));
    }
}
