package ru.yandex.practicum.mybankfront.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.yandex.practicum.mybankfront.controller.dto.AccountDto;

import java.util.List;

@Component
public class AccountsClient extends AbstractUserBearerClient {

    private final RestClient restClient;

    public AccountsClient(RestClient.Builder builder,
                          OAuth2AuthorizedClientService authorizedClients,
                          @Value("${bank.gateway-url}") String gatewayUrl) {
        super(authorizedClients);
        this.restClient = builder.baseUrl(gatewayUrl).build();
    }

    @CircuitBreaker(name = "accounts")
    public Profile getMe() {
        return restClient.get()
                .uri("/accounts/me")
                .headers(h -> h.setBearerAuth(accessToken()))
                .retrieve()
                .body(Profile.class);
    }

    @CircuitBreaker(name = "accounts")
    public List<AccountDto> others() {
        return restClient.get()
                .uri("/accounts/others")
                .headers(h -> h.setBearerAuth(accessToken()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountDto>>() {});
    }

    @CircuitBreaker(name = "accounts")
    public Profile updateMe(ProfileUpdate update) {
        return restClient.put()
                .uri("/accounts/me")
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(update)
                .retrieve()
                .body(Profile.class);
    }
}
