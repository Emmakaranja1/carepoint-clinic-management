package com.carepoint.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.mail.MailService;
import com.carepoint.identity.application.GoogleIdentityVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
// high limits so the rate-limit filter stays out of these tests;
// RateLimitIT covers limiting with its own tight limit
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000"})
class AuthApiIT {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final Pattern CODE_IN_BODY = Pattern.compile("is: (\\d{6})");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    // mail + google verification are faked: the code travels via the captured body
    @MockitoBean
    MailService mail;

    @MockitoBean
    GoogleIdentityVerifier google;

    // ---------- registration ----------

    @Test
    void register_createsPatientAccount_pendingVerification() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"Reg User"}
                                """, email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.message").value(
                        "Registration successful — we've emailed you a verification code"));

        // login is gated until the emailed code verifies the address
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"%s\"}", email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.local";
        String payload = """
                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"Dup"}
                """.formatted(email, PASSWORD, PASSWORD);
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
    void register_weakPassword_reportsUnmetRules() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"x@test.local","password":"short","confirmPassword":"short","fullName":"X"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void register_confirmMismatch_returnsFieldError() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"mm@test.local","password":"%s","confirmPassword":"Different-1","fullName":"X"}
                                """, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.confirmPassword").value("Passwords do not match"));
    }

    // ---------- email verification OTP ----------

    @Test
    void verifyEmail_correctCode_marksVerified_andIssuesSession() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@test.local";
        register(email, PASSWORD);
        String code = lastCodeSentTo(email);

        MvcResult result = mvc.perform(post("/api/v1/auth/verify-email")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"code\":\"%s\"}", email, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        assertThat(cookieValue(result, "cp_access")).isNotBlank();

        // and the code cannot be replayed
        mvc.perform(post("/api/v1/auth/verify-email")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"code\":\"%s\"}", email, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void verifyEmail_fourWrongAttempts_killsTheCode() throws Exception {
        String email = "att-" + UUID.randomUUID() + "@test.local";
        register(email, PASSWORD);
        String goodCode = lastCodeSentTo(email);
        String badCode = goodCode.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/v1/auth/verify-email")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("{\"email\":\"%s\",\"code\":\"%s\"}", email, badCode)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }
        // attempts exhausted — even the correct code is now rejected
        mvc.perform(post("/api/v1/auth/verify-email")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"code\":\"%s\"}", email, goodCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void verifyEmail_expiredCode_rejected() throws Exception {
        String email = "exp-" + UUID.randomUUID() + "@test.local";
        register(email, PASSWORD);
        String code = lastCodeSentTo(email);
        jdbc.update("UPDATE otp_codes SET expires_at = now() - interval '1 second' WHERE email = ?", email);

        mvc.perform(post("/api/v1/auth/verify-email")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"code\":\"%s\"}", email, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void resendCode_fourSends_thenCooldown() throws Exception {
        String email = "rs-" + UUID.randomUUID() + "@test.local";
        register(email, PASSWORD); // send #1

        for (int i = 0; i < 3; i++) { // sends #2, #3, #4
            mvc.perform(post("/api/v1/auth/resend-code")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("{\"email\":\"%s\"}", email)))
                    .andExpect(status().isNoContent());
        }
        // 5th send inside the window → cooldown kicks in (104 s)
        mvc.perform(post("/api/v1/auth/resend-code")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\"}", email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // unknown emails get the same 204 — no enumeration
        mvc.perform(post("/api/v1/auth/resend-code")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"ghost-%s@test.local\"}", UUID.randomUUID())))
                .andExpect(status().isNoContent());
    }

    // ---------- forgot / reset password ----------

    @Test
    void resetPassword_validCode_changesPassword_andKillsSessions() throws Exception {
        String email = "rst-" + UUID.randomUUID() + "@test.local";
        registerAndVerify(email, PASSWORD);
        MvcResult login = login(email, PASSWORD);
        String refresh = cookieValue(login, "cp_refresh");

        mvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\"}", email)))
                .andExpect(status().isNoContent());
        String code = lastCodeSentTo(email);

        String newPassword = "New-Horse-42";
        mvc.perform(post("/api/v1/auth/reset-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","code":"%s","password":"%s","confirmPassword":"%s"}
                                """, email, code, newPassword, newPassword)))
                .andExpect(status().isNoContent());

        // every pre-reset session is dead
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("cp_refresh", refresh)))
                .andExpect(status().isUnauthorized());

        // old password rejected, new password works
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"%s\"}", email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(email, newPassword);
    }

    @Test
    void forgotPassword_unknownEmail_same204() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"ghost-%s@test.local\"}", UUID.randomUUID())))
                .andExpect(status().isNoContent());
    }

    // ---------- google sign-in ----------

    @Test
    void googleLogin_newUser_createsVerifiedAccount() throws Exception {
        String email = "g-" + UUID.randomUUID() + "@test.local";
        when(google.verify("good-token"))
                .thenReturn(new GoogleIdentityVerifier.GoogleIdentity(email, "G User", true));

        MvcResult result = mvc.perform(post("/api/v1/auth/google")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"good-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        assertThat(cookieValue(result, "cp_access")).isNotBlank();
    }

    @Test
    void googleLogin_linksUnverifiedLocalAccount() throws Exception {
        String email = "link-" + UUID.randomUUID() + "@test.local";
        register(email, PASSWORD); // unverified — password login would 403
        when(google.verify("link-token"))
                .thenReturn(new GoogleIdentityVerifier.GoogleIdentity(email, "Link User", true));

        mvc.perform(post("/api/v1/auth/google")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"link-token\"}"))
                .andExpect(status().isOk());

        // Google's verified claim settled the email — password login now works
        login(email, PASSWORD);
    }

    @Test
    void googleLogin_badToken_401() throws Exception {
        when(google.verify("bogus"))
                .thenThrow(new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID, "Invalid Google token"));

        mvc.perform(post("/api/v1/auth/google")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bogus\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_TOKEN_INVALID"));
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
        registerAndVerify(email, PASSWORD);

        MvcResult login = login(email, PASSWORD);

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
        registerAndVerify(email, PASSWORD);

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
        registerAndVerify(email, PASSWORD);

        MvcResult login = login(email, PASSWORD);
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
        registerAndVerify(email, PASSWORD);
        MvcResult login = login(email, PASSWORD);
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
                                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"T"}
                                """, email, password, password)))
                .andExpect(status().isCreated());
    }

    private void registerAndVerify(String email, String password) throws Exception {
        register(email, password);
        mvc.perform(post("/api/v1/auth/verify-email")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"code\":\"%s\"}", email, lastCodeSentTo(email))))
                .andExpect(status().isOk());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** The OTP rides inside the mail body — grab the most recent send to this address. */
    private String lastCodeSentTo(String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), body.capture());
        String last = body.getAllValues().get(body.getAllValues().size() - 1);
        Matcher m = CODE_IN_BODY.matcher(last);
        assertThat(m.find()).as("OTP present in mail body").isTrue();
        return m.group(1);
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
