package ru.yandex.practicum.mybank.accounts;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import ru.yandex.practicum.mybank.accounts.domain.Account;
import ru.yandex.practicum.mybank.accounts.domain.AccountRepository;
import ru.yandex.practicum.mybank.accounts.outbox.OutboxRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@ActiveProfiles({"test", "contract"})
@Import(ContractsBase.PermitAllSecurity.class)
public abstract class ContractsBase extends AbstractAccountsIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private OutboxRepository outbox;

    @BeforeEach
    public void contractSetUp() {
        outbox.deleteAll();
        accounts.deleteAll();
        Account alice = new Account("alice");
        alice.setFirstName("Alice");
        alice.setLastName("Andreeva");
        alice.setDob(LocalDate.of(1990, 4, 12));
        alice.setBalance(new BigDecimal("1000.00"));
        accounts.save(alice);
        RestAssuredMockMvc.webAppContextSetup(context);
    }

    @TestConfiguration
    static class PermitAllSecurity {
        @Bean
        @Primary
        public SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
    }
}
