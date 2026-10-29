package com.carepoint.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.carepoint.common.mail.MailService;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.auth.session-idle-timeout=15m",
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000"})
class SessionIdleIT {

    private static final String PASSWORD = "Correct-Horse-9";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    MailService mail;

    @Test
    void refreshCookieLivesOnlyForTheIdleWindow() throws Exception {
        MvcResult login = login(newUser());
        assertThat(setCookie(login, "cp_refresh")).contains("Max-Age=900");
        assertThat(setCookie(login, "cp_access")).contains("Max-Age=300");
    }

    @Test
    void refreshWithinIdleWindow_slidesTheExpiry() throws Exception {
        String email = newUser();
        Cookie refresh = cookie(login(email), "cp_refresh");
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() + interval '1 minute' "
                + "WHERE user_id = (SELECT id FROM users WHERE email = ?)", email);

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());

        Instant newest = jdbc.queryForObject(
                "SELECT max(expires_at) FROM refresh_tokens WHERE revoked_at IS NULL "
                        + "AND user_id = (SELECT id FROM users WHERE email = ?)", Instant.class, email);
        assertThat(Duration.between(Instant.now(), newest)).isGreaterThan(Duration.ofMinutes(14));
    }

    @Test
    void refreshAfterIdleWindow_isRejected() throws Exception {
        String email = newUser();
        Cookie refresh = cookie(login(email), "cp_refresh");
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second' "
                + "WHERE user_id = (SELECT id FROM users WHERE email = ?)", email);

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    private String newUser() throws Exception {
        String email = "idle-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"Idle User"}
                                """.formatted(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    private MvcResult login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow();
    }

    private Cookie cookie(MvcResult result, String name) {
        String header = setCookie(result, name);
        return new Cookie(name, header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }
}
