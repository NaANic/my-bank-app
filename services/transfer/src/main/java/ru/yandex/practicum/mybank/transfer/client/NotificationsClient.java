package ru.yandex.practicum.mybank.transfer.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class NotificationsClient {

    static final String REGISTRATION_ID = "notifications-service";

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public NotificationsClient(RestClient.Builder loadBalancedRestClientBuilder,
                               OAuth2AuthorizedClientManager authorizedClientManager,
                               @Value("${bank.notifications-base-url}") String baseUrl) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
        this.authorizedClientManager = authorizedClientManager;
    }

    @CircuitBreaker(name = "notifications")
    public void send(String login, String kind, String message) {
        String token = serviceToken();
        restClient.post()
                .uri("/notifications")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("login", login, "kind", kind, "message", message))
                .retrieve()
                .toBodilessEntity();
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
