package ru.yandex.practicum.mybankfront.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class CashClient {

    private final RestClient restClient;
    private final OAuth2AuthorizedClientService authorizedClients;

    public CashClient(RestClient.Builder builder,
                      OAuth2AuthorizedClientService authorizedClients,
                      @Value("${bank.gateway-url}") String gatewayUrl) {
        this.restClient = builder.baseUrl(gatewayUrl).build();
        this.authorizedClients = authorizedClients;
    }

    public Profile deposit(BigDecimal amount) {
        return call("/cash/deposit", amount);
    }

    public Profile withdraw(BigDecimal amount) {
        return call("/cash/withdraw", amount);
    }

    private Profile call(String path, BigDecimal amount) {
        return restClient.post()
                .uri(path)
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amount))
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
