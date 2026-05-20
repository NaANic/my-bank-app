package ru.yandex.practicum.mybank.cash.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.yandex.practicum.mybank.cash.api.AccountSnapshot;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AccountsClient {

    static final String REGISTRATION_ID = "accounts-service";

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public AccountsClient(RestClient.Builder loadBalancedRestClientBuilder,
                          OAuth2AuthorizedClientManager authorizedClientManager,
                          @Value("${bank.accounts-base-url}") String baseUrl) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
        this.authorizedClientManager = authorizedClientManager;
    }

    public AccountSnapshot credit(String login, BigDecimal amount) {
        return call("credit", login, amount);
    }

    public AccountSnapshot debit(String login, BigDecimal amount) {
        return call("debit", login, amount);
    }

    private AccountSnapshot call(String operation, String login, BigDecimal amount) {
        String token = serviceToken();
        try {
            return restClient.post()
                    .uri("/internal/accounts/{login}/{op}", login, operation)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amount))
                    .retrieve()
                    .body(AccountSnapshot.class);
        } catch (RestClientResponseException e) {
            throw new AccountsServiceException(e.getStatusCode(), e.getResponseBodyAsString());
        }
    }

    private String serviceToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(REGISTRATION_ID)
                .build();
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (client == null) {
            throw new IllegalStateException("Could not obtain service token for registration '" + REGISTRATION_ID + "'");
        }
        return client.getAccessToken().getTokenValue();
    }
}
