package ru.yandex.practicum.mybankfront.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import ru.yandex.practicum.mybankfront.client.AccountsClient;
import ru.yandex.practicum.mybankfront.client.CashClient;
import ru.yandex.practicum.mybankfront.client.Profile;
import ru.yandex.practicum.mybankfront.client.TransferClient;
import ru.yandex.practicum.mybankfront.controller.dto.AccountDto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = MainController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
})
class MainControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountsClient accountsClient;
    @MockitoBean CashClient cashClient;
    @MockitoBean TransferClient transferClient;

    @Test
    void getAccount_rendersProfileAndPeers() throws Exception {
        when(accountsClient.getMe()).thenReturn(new Profile("alice", "Alice", "Andreeva",
                LocalDate.of(1990, 4, 12), new BigDecimal("1000.00")));
        when(accountsClient.others()).thenReturn(List.of(new AccountDto("bob", "Borisov Bob")));

        mockMvc.perform(get("/account"))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("name", "Andreeva Alice"))
                .andExpect(model().attribute("birthdate", "1990-04-12"))
                .andExpect(model().attribute("sum", new BigDecimal("1000.00")))
                .andExpect(model().attribute("accounts", List.of(new AccountDto("bob", "Borisov Bob"))));
    }

    @Test
    void getAccount_degradesGracefullyWhenAccountsDown() throws Exception {
        when(accountsClient.getMe()).thenThrow(
                HttpClientErrorException.create(BAD_REQUEST, "Bad", null, new byte[0], StandardCharsets.UTF_8));

        mockMvc.perform(get("/account"))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attributeExists("errors"));
    }

    @Test
    void editCash_depositRedirectsToAccountAndCarriesInfoFlash() throws Exception {
        when(cashClient.deposit(any(BigDecimal.class)))
                .thenReturn(new Profile("alice", "Alice", "Andreeva",
                        LocalDate.of(1990, 4, 12), new BigDecimal("1100.00")));

        mockMvc.perform(post("/cash").param("value", "100").param("action", "PUT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"))
                .andExpect(flash().attribute("info", "Счёт пополнен на 100 руб."));
    }

    @Test
    void editCash_insufficientFundsRedirectsWithErrorFlash() throws Exception {
        byte[] body = "{\"error\":\"insufficient_funds\",\"message\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        when(cashClient.withdraw(any(BigDecimal.class)))
                .thenThrow(HttpClientErrorException.create(BAD_REQUEST, "Bad", null, body, StandardCharsets.UTF_8));

        mockMvc.perform(post("/cash").param("value", "9999").param("action", "GET"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"))
                .andExpect(flash().attribute("errors", List.of("Недостаточно средств на счёте")));
    }

    @Test
    void editAccount_rejectsUnderageWithFlashError() throws Exception {
        mockMvc.perform(post("/account")
                        .param("name", "Под Росток")
                        .param("birthdate", LocalDate.now().minusYears(10).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"))
                .andExpect(flash().attribute("errors",
                        org.hamcrest.Matchers.hasItem("Возраст должен быть не меньше 18 лет")));
    }
}
