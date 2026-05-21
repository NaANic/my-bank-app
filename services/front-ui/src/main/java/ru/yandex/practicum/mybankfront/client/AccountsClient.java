package ru.yandex.practicum.mybankfront.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import ru.yandex.practicum.mybankfront.controller.dto.AccountDto;

@Component
public class AccountsClient {

    private final RestClient restClient;
    private final OAuth2AuthorizedClientService authorizedClients;

    public AccountsClient(RestClient.Builder builder,
                          OAuth2AuthorizedClientService authorizedClients,
                          @Value("${bank.gateway-url}") String gatewayUrl) {
        this.restClient = builder.baseUrl(gatewayUrl).build();
        this.authorizedClients = authorizedClients;
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

    private String accessToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OAuth2AuthenticationToken token)) {
            throw new IllegalStateException("No OAuth2 authentication in security context");
        }
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(), token.getName());
        if (client == null) {
            throw new IllegalStateException("No authorized client for principal " + token.getName());
        }
        return client.getAccessToken().getTokenValue();
    }
}
