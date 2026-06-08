package ru.yandex.practicum.mybank.cash.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class NotificationsClient extends AbstractServiceClient {

    static final String REGISTRATION_ID = "notifications-service";

    private final RestClient restClient;

    public NotificationsClient(RestClient.Builder restClientBuilder,
                               OAuth2AuthorizedClientManager authorizedClientManager,
                               @Value("${bank.notifications-base-url}") String baseUrl) {
        super(authorizedClientManager);
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @CircuitBreaker(name = "notifications")
    public void send(String login, String kind, String message) {
        String token = serviceToken(REGISTRATION_ID);
        restClient.post()
                .uri("/notifications")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("login", login, "kind", kind, "message", message))
                .retrieve()
                .toBodilessEntity();
    }
}
