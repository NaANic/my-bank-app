package ru.yandex.practicum.mybankfront.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class TransferClient extends AbstractUserBearerClient {

    private final RestClient restClient;

    public TransferClient(RestClient.Builder builder,
                          OAuth2AuthorizedClientService authorizedClients,
                          @Value("${bank.gateway-url}") String gatewayUrl) {
        super(authorizedClients);
        this.restClient = builder.baseUrl(gatewayUrl).build();
    }

    @CircuitBreaker(name = "transfer")
    public Profile transfer(String toLogin, BigDecimal amount) {
        return restClient.post()
                .uri("/transfer/execute")
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("toLogin", toLogin, "amount", amount))
                .retrieve()
                .body(Profile.class);
    }
}
