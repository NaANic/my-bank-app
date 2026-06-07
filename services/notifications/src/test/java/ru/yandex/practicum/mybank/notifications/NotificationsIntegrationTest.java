package ru.yandex.practicum.mybank.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.yandex.practicum.mybank.notifications.domain.NotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationsIntegrationTest extends AbstractNotificationsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired NotificationRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void post_requiresAuth() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"kind\":\"deposit\",\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_rejectsUserJwtWithoutServiceScope() throws Exception {
        mockMvc.perform(post("/notifications")
                        .with(jwt().jwt(b -> b.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"kind\":\"deposit\",\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_persistsWithServiceScope() throws Exception {
        mockMvc.perform(post("/notifications")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"kind\":\"deposit\",\"message\":\"+100\"}"))
                .andExpect(status().isAccepted());

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAll().get(0).getMessage()).isEqualTo("+100");
    }

    @Test
    void post_rejectsBlankFields() throws Exception {
        mockMvc.perform(post("/notifications")
                        .with(serviceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"\",\"kind\":\"\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor serviceJwt() {
        return jwt().jwt(b -> b.claim("scope", "bank:service"))
                .authorities(new SimpleGrantedAuthority("SCOPE_bank:service"));
    }
}
