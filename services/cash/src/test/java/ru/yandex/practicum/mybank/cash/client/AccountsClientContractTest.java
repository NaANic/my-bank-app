package ru.yandex.practicum.mybank.cash.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "ru.yandex.practicum:accounts:0.0.1-SNAPSHOT:stubs:6666",
        stubsMode = StubRunnerProperties.StubsMode.CLASSPATH
)
@TestPropertySource(properties = {
        "bank.accounts-base-url=http://localhost:6666"
})
class AccountsClientContractTest {

    @Autowired AccountsClient client;
    @MockitoBean OAuth2AuthorizedClientManager authorizedClientManager;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RestClient.Builder plainRestClientBuilder() {
            return RestClient.builder();
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void stubAuthorizedClient() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("accounts-service")
                .clientId("cash")
                .clientSecret("test")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:0/token")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "fake-token", Instant.now(), Instant.now().plusSeconds(60));
        OAuth2AuthorizedClient authorized = new OAuth2AuthorizedClient(registration, "cash", accessToken);
        when(authorizedClientManager.authorize(any())).thenReturn(authorized);
    }

    @Test
    void credit_matchesProducerContract() {
        var snapshot = client.credit("alice", new BigDecimal("50.00"));

        assertThat(snapshot.login()).isEqualTo("alice");
        assertThat(snapshot.firstName()).isEqualTo("Alice");
        assertThat(snapshot.balance()).isEqualByComparingTo("1050.00");
    }

    @Test
    void debit_overdraftSurfacesAsAccountsServiceException() {
        assertThatThrownBy(() -> client.debit("alice", new BigDecimal("99999.00")))
                .isInstanceOf(AccountsServiceException.class)
                .hasMessageContaining("insufficient_funds");
    }
}
