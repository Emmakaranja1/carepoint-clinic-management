package com.carepoint.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000"})
class AccountApiIT {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final String NEW_PASSWORD = "Battery-Staple-7";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MailService mail;

    @Test
    void me_exposesPhoneAndPasswordSet() throws Exception {
        Session s = newUser();
        mvc.perform(get("/api/v1/auth/me").cookie(s.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+254712345678"))
                .andExpect(jsonPath("$.passwordSet").value(true));
    }

    @Test
    void updateProfile_persistsNameAndPhone() throws Exception {
        Session s = newUser();
        mvc.perform(patch("/api/v1/auth/me").cookie(s.access())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"  Wanjiku Demo \",\"phone\":\"+254798765432\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Wanjiku Demo"))
                .andExpect(jsonPath("$.phone").value("+254798765432"));

        mvc.perform(get("/api/v1/auth/me").cookie(s.access()))
                .andExpect(jsonPath("$.fullName").value("Wanjiku Demo"));
    }

    @Test
    void updateProfile_blankPhoneClearsIt() throws Exception {
        Session s = newUser();
        mvc.perform(patch("/api/v1/auth/me").cookie(s.access())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Demo\",\"phone\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void updateProfile_rejectsInvalidInput() throws Exception {
        Session s = newUser();
        mvc.perform(patch("/api/v1/auth/me").cookie(s.access())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"phone\":\"0712\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.fullName").exists())
                .andExpect(jsonPath("$.fields.phone").exists());
    }

    @Test
    void updateProfile_requiresAuthentication() throws Exception {
        mvc.perform(patch("/api/v1/auth/me")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_requiresCsrfHeader() throws Exception {
        Session s = newUser();
        mvc.perform(patch("/api/v1/auth/me").cookie(s.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_wrongCurrent_returnsFieldError() throws Exception {
        Session s = newUser();
        changePassword(s, "Wrong-Pass-1", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.currentPassword").value("Current password is incorrect"));
    }

    @Test
    void changePassword_weakOrMismatched_returnsFieldErrors() throws Exception {
        Session s = newUser();
        changePassword(s, PASSWORD, "weak", "weak")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
        changePassword(s, PASSWORD, NEW_PASSWORD, "Different-9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.confirmPassword").value("Passwords do not match"));
    }

    @Test
    void changePassword_rotatesCredentialsAndRevokesOtherSessions() throws Exception {
        Session s = newUser();
        Session other = login(s.email(), PASSWORD);

        MvcResult result = changePassword(s, PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie freshAccess = cookie(result, "cp_access");

        mvc.perform(get("/api/v1/auth/me").cookie(freshAccess)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").cookie(other.refresh())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(s.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(s.email(), NEW_PASSWORD);
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(
            Session s, String current, String next, String confirm) throws Exception {
        return mvc.perform(post("/api/v1/auth/change-password").cookie(s.access())
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                        .formatted(current, next, confirm)));
    }

    private Session newUser() throws Exception {
        String email = "acct-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"Acct User","phone":"+254712345678"}
                                """.formatted(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    private Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(email, cookie(result, "cp_access"), cookie(result, "cp_refresh"));
    }

    private Cookie cookie(MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow();
        return new Cookie(name, header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }

    private record Session(String email, Cookie access, Cookie refresh) {
    }
}
