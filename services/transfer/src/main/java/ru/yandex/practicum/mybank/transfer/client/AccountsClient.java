package ru.yandex.practicum.mybank.transfer.client;

import ru.yandex.practicum.mybank.common.AbstractServiceClient;
import ru.yandex.practicum.mybank.common.AccountsServiceException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.yandex.practicum.mybank.common.AccountSnapshot;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AccountsClient extends AbstractServiceClient {

    static final String REGISTRATION_ID = "accounts-service";

    private final RestClient restClient;

    public AccountsClient(RestClient.Builder restClientBuilder,
                          OAuth2AuthorizedClientManager authorizedClientManager,
                          @Value("${bank.accounts-base-url}") String baseUrl) {
        super(authorizedClientManager);
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @CircuitBreaker(name = "accounts")
    public AccountSnapshot transfer(String fromLogin, String toLogin, BigDecimal amount) {
        String token = serviceToken(REGISTRATION_ID);
        try {
            return restClient.post()
                    .uri("/internal/accounts/transfers")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fromLogin", fromLogin, "toLogin", toLogin, "amount", amount))
                    .retrieve()
                    .body(AccountSnapshot.class);
        } catch (RestClientResponseException e) {
            throw new AccountsServiceException(e.getStatusCode(), e.getResponseBodyAsString());
        }
    }
}
