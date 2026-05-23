package ru.yandex.practicum.mybank.cash.client;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

abstract class AbstractServiceClient {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    protected AbstractServiceClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    protected String serviceToken(String registrationId) {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal(registrationId)
                .build();
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (client == null) {
            throw new IllegalStateException(
                    "Could not obtain service token for registration '" + registrationId + "'");
        }
        return client.getAccessToken().getTokenValue();
    }
}
