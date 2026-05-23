package ru.yandex.practicum.mybankfront.client;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

abstract class AbstractUserBearerClient {

    private final OAuth2AuthorizedClientService authorizedClients;

    protected AbstractUserBearerClient(OAuth2AuthorizedClientService authorizedClients) {
        this.authorizedClients = authorizedClients;
    }

    protected String accessToken() {
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
