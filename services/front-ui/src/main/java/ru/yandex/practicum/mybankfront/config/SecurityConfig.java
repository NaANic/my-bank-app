package ru.yandex.practicum.mybankfront.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class SecurityConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            OAuth2ClientProperties properties,
            @Value("${bank.keycloak.end-session-uri:http://localhost:8090/realms/bank/protocol/openid-connect/logout}")
            String endSessionUri) {

        Map<String, Object> metadata = Map.of("end_session_endpoint", endSessionUri);
        List<ClientRegistration> patched = new OAuth2ClientPropertiesMapper(properties)
                .asClientRegistrations().values().stream()
                .map(original -> withMetadata(original, metadata))
                .toList();
        return new InMemoryClientRegistrationRepository(patched);
    }

    private static ClientRegistration withMetadata(ClientRegistration original, Map<String, Object> metadata) {
        ClientRegistration.ProviderDetails provider = original.getProviderDetails();
        ClientRegistration.ProviderDetails.UserInfoEndpoint userInfo = provider.getUserInfoEndpoint();
        return ClientRegistration.withRegistrationId(original.getRegistrationId())
                .clientId(original.getClientId())
                .clientSecret(original.getClientSecret())
                .clientAuthenticationMethod(original.getClientAuthenticationMethod())
                .authorizationGrantType(original.getAuthorizationGrantType())
                .redirectUri(original.getRedirectUri())
                .scope(original.getScopes())
                .clientName(original.getClientName())
                .authorizationUri(provider.getAuthorizationUri())
                .tokenUri(provider.getTokenUri())
                .userInfoUri(userInfo == null ? null : userInfo.getUri())
                .userNameAttributeName(userInfo == null ? null : userInfo.getUserNameAttributeName())
                .jwkSetUri(provider.getJwkSetUri())
                .issuerUri(provider.getIssuerUri())
                .providerConfigurationMetadata(metadata)
                .build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ClientRegistrationRepository clients) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(o -> o.defaultSuccessUrl("/account", true))
                .logout(l -> l.logoutSuccessHandler(oidcLogoutSuccessHandler(clients)));
        return http.build();
    }

    private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clients) {
        OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(clients);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
