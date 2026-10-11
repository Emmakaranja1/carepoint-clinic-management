package com.carepoint.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
// high limits so the rate-limit filter stays out of these tests;
// RateLimitIT covers limiting with its own tight limit
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000"})
class AuthApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    // ---------- registration ----------

    @Test
    void register_createsPatientAccount() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","password":"correct-horse-9","fullName":"Reg User"}
                                """, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.clinicRoles").isMap());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.local";
        String payload = """
                {"email":"%s","password":"correct-horse-9","fullName":"Dup"}
                """.formatted(email);
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void register_shortPassword_returnsValidationError() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"x@test.local","password":"short","fullName":"X"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").exists());
    }

    // ---------- CSRF ----------

    @Test
    void mutation_withoutCustomHeader_rejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_MISSING"));
    }

    // ---------- login + me ----------

    @Test
    void login_setsHttpOnlyCookies_andMeWorks() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@test.local";
        register(email, "correct-horse-9");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","password":"correct-horse-9"}
                                """, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        String access = cookieValue(login, "cp_access");
        String refresh = cookieValue(login, "cp_refresh");
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        assertThat(setCookieHeader(login, "cp_access")).contains("HttpOnly").contains("SameSite=Lax");

        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("cp_access", access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void login_badCredentials_sameUniform401() throws Exception {
        String email = "uniform-" + UUID.randomUUID() + "@test.local";
        register(email, "correct-horse-9");

        for (String body : new String[] {
                body("{\"email\":\"%s\",\"password\":\"wrong\"}", email),
                "{\"email\":\"nobody-" + UUID.randomUUID() + "@test.local\",\"password\":\"whatever\"}"}) {
            mvc.perform(post("/api/v1/auth/login")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }
    }

    @Test
    void me_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- refresh rotation + reuse detection ----------

    @Test
    void refresh_rotatesToken_andReuseRevokesFamily() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@test.local";
        register(email, "correct-horse-9");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"correct-horse-9\"}", email)))
                .andReturn();
        String refresh1 = cookieValue(login, "cp_refresh");

        // legitimate rotation
        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("cp_refresh", refresh1)))
                .andExpect(status().isNoContent())
                .andReturn();
        String refresh2 = cookieValue(rotated, "cp_refresh");
        assertThat(refresh2).isNotBlank().isNotEqualTo(refresh1);

        // attacker replays the stolen (already-rotated) token → 401 + family revocation
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("cp_refresh", refresh1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // the legitimate holder's new token is dead too — whole family revoked
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("cp_refresh", refresh2)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- logout ----------

    @Test
    void logout_revokesSession() throws Exception {
        String email = "logout-" + UUID.randomUUID() + "@test.local";
        register(email, "correct-horse-9");
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"correct-horse-9\"}", email)))
                .andReturn();
        String refresh = cookieValue(login, "cp_refresh");

        mvc.perform(post("/api/v1/auth/logout")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("cp_refresh", refresh)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("cp_refresh", refresh)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private void register(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","password":"%s","fullName":"T"}
                                """, email, password)))
                .andExpect(status().isCreated());
    }

    private String body(String template, Object... args) {
        return String.format(template, args);
    }

    private String cookieValue(MvcResult result, String name) {
        String header = setCookieHeader(result, name);
        return header == null ? null
                : header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private String setCookieHeader(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }
}
