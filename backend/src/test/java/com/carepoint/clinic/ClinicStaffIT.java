package com.carepoint.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.carepoint.common.mail.MailService;
import com.carepoint.identity.application.GoogleIdentityVerifier;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000"})
class ClinicStaffIT {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final Pattern CODE_IN_BODY = Pattern.compile("is: (\\d{6})");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MailService mail;

    @MockitoBean
    GoogleIdentityVerifier google;

    // ---------- clinic creation ----------

    @Test
    void createClinic_verifiedUser_becomesAdmin() throws Exception {
        Cookie access = verifiedSession("owner-" + UUID.randomUUID() + "@test.local");
        mvc.perform(post("/api/v1/clinics")
                        .cookie(access)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Clinic One\",\"timezone\":\"Africa/Nairobi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("test-clinic-one"))
                .andExpect(jsonPath("$.message").isString());

        mvc.perform(get("/api/v1/clinics/mine").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("CLINIC_ADMIN"))
                .andExpect(jsonPath("$[0].clinicName").value("Test Clinic One"));
    }

    @Test
    void createClinic_unverifiedUser_403() throws Exception {
        // only Google can mint a session for an unverified email
        String email = "gu-" + UUID.randomUUID() + "@test.local";
        when(google.verify("gu-token"))
                .thenReturn(new GoogleIdentityVerifier.GoogleIdentity(email, "U", false));
        MvcResult login = mvc.perform(post("/api/v1/auth/google")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"gu-token\"}"))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(post("/api/v1/clinics")
                        .cookie(new Cookie("cp_access", cookieValue(login, "cp_access")))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void createClinic_badTimezone_400() throws Exception {
        Cookie access = verifiedSession("tz-" + UUID.randomUUID() + "@test.local");
        mvc.perform(post("/api/v1/clinics")
                        .cookie(access)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.timezone").exists());
    }

    @Test
    void createClinic_sameName_getsUniqueSlug() throws Exception {
        Cookie access = verifiedSession("dup-" + UUID.randomUUID() + "@test.local");
        String body = "{\"name\":\"Repeat Clinic\",\"timezone\":\"UTC\"}";
        for (String expected : new String[] {"repeat-clinic", "repeat-clinic-2"}) {
            mvc.perform(post("/api/v1/clinics").cookie(access)
                            .header("X-Requested-With", "XMLHttpRequest")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.slug").value(expected));
        }
    }

    // ---------- authz matrix: patients and cross-clinic staff ----------

    @Test
    void patientOnlyUser_staffEndpoint_403() throws Exception {
        String clinicId = createClinicAs(verifiedSession("a-" + UUID.randomUUID() + "@test.local"));
        Cookie patient = verifiedSession("p-" + UUID.randomUUID() + "@test.local");

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/staff").cookie(patient))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void staffOfOtherClinic_staffEndpoint_404_notLeaked() throws Exception {
        String clinicA = createClinicAs(verifiedSession("sa-" + UUID.randomUUID() + "@test.local"));
        Cookie adminB = verifiedSession("sb-" + UUID.randomUUID() + "@test.local");
        createClinicAs(adminB); // B is staff — just not at clinic A

        mvc.perform(get("/api/v1/clinics/" + clinicA + "/staff").cookie(adminB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------- staff invites ----------

    @Test
    void inviteStaff_newUser_setPasswordCode_thenLogin() throws Exception {
        Cookie admin = verifiedSession("inv-" + UUID.randomUUID() + "@test.local");
        String clinicId = createClinicAs(admin);
        String staffEmail = "doc-" + UUID.randomUUID() + "@test.local";

        mvc.perform(post("/api/v1/clinics/" + clinicId + "/staff")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","fullName":"Dr New","role":"DOCTOR"}
                                """, staffEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCTOR"));

        // invited staff set their password via the mailed OTP, then log in
        setPassword(staffEmail, lastCodeSentTo(staffEmail));
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"%s\"}", staffEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicRoles." + clinicId).value("DOCTOR"))
                .andReturn();
        assertThat(cookieValue(login, "cp_access")).isNotBlank();
    }

    @Test
    void inviteStaff_existingUser_membershipAdded() throws Exception {
        Cookie admin = verifiedSession("ex-" + UUID.randomUUID() + "@test.local");
        String clinicId = createClinicAs(admin);
        String staffEmail = "exstaff-" + UUID.randomUUID() + "@test.local";
        verifiedSession(staffEmail); // pre-existing verified patient account

        mvc.perform(post("/api/v1/clinics/" + clinicId + "/staff")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","fullName":"Existing","role":"RECEPTIONIST"}
                                """, staffEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));

        // re-inviting the same person is rejected — use PATCH to change roles
        mvc.perform(post("/api/v1/clinics/" + clinicId + "/staff")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","fullName":"Existing","role":"DOCTOR"}
                                """, staffEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").isString());
    }

    @Test
    void inviteStaff_byReceptionist_403() throws Exception {
        Cookie admin = verifiedSession("radm-" + UUID.randomUUID() + "@test.local");
        String clinicId = createClinicAs(admin);
        String recEmail = "recep-" + UUID.randomUUID() + "@test.local";
        invite(clinicId, admin, recEmail, "RECEPTIONIST");

        // receptionist sets password, logs in, tries to invite — admin-only
        Cookie recep = loginAfterInvite(recEmail);
        mvc.perform(post("/api/v1/clinics/" + clinicId + "/staff")
                        .cookie(recep)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@test.local\",\"fullName\":\"X\",\"role\":\"DOCTOR\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- staff updates ----------

    @Test
    void updateStaff_changeRole_andDisable() throws Exception {
        Cookie admin = verifiedSession("upd-" + UUID.randomUUID() + "@test.local");
        String clinicId = createClinicAs(admin);
        String membershipId = invite(clinicId, admin,
                "chg-" + UUID.randomUUID() + "@test.local", "DOCTOR");

        mvc.perform(patch("/api/v1/clinics/" + clinicId + "/staff/" + membershipId)
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RECEPTIONIST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));

        mvc.perform(patch("/api/v1/clinics/" + clinicId + "/staff/" + membershipId)
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void updateStaff_selfModification_rejected() throws Exception {
        Cookie admin = verifiedSession("self-" + UUID.randomUUID() + "@test.local");
        String clinicId = createClinicAs(admin);
        String membershipId = jsonField(staffJson(clinicId, admin), "membershipId");

        mvc.perform(patch("/api/v1/clinics/" + clinicId + "/staff/" + membershipId)
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DOCTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.membership").isString());
    }

    @Test
    void updateStaff_disableSecondAdmin_leavesClinicUsable() throws Exception {
        Cookie admin = verifiedSession("multi-" + UUID.randomUUID() + "@test.local");
        String clinicId = createClinicAs(admin);
        String secondAdmin = invite(clinicId, admin,
                "adm2-" + UUID.randomUUID() + "@test.local", "CLINIC_ADMIN");

        mvc.perform(patch("/api/v1/clinics/" + clinicId + "/staff/" + secondAdmin)
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateStaff_membershipOfOtherClinic_404() throws Exception {
        Cookie adminA = verifiedSession("xa-" + UUID.randomUUID() + "@test.local");
        String clinicA = createClinicAs(adminA);
        String foreignMembership = invite(clinicA, adminA,
                "fm-" + UUID.randomUUID() + "@test.local", "DOCTOR");

        Cookie adminB = verifiedSession("xb-" + UUID.randomUUID() + "@test.local");
        String clinicB = createClinicAs(adminB);
        // admin B can't touch clinic A's membership through clinic B's path
        mvc.perform(patch("/api/v1/clinics/" + clinicB + "/staff/" + foreignMembership)
                        .cookie(adminB)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CLINIC_ADMIN\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String staffJson(String clinicId, Cookie cookie) throws Exception {
        return mvc.perform(get("/api/v1/clinics/" + clinicId + "/staff").cookie(cookie))
                .andReturn().getResponse().getContentAsString();
    }

    /** Registers, verifies via OTP, returns the cp_access cookie. */
    private Cookie verifiedSession(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"T"}
                                """, email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/v1/auth/verify-email")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"code\":\"%s\"}", email, lastCodeSentTo(email))))
                .andExpect(status().isOk())
                .andReturn();
        return new Cookie("cp_access", cookieValue(r, "cp_access"));
    }

    private String createClinicAs(Cookie access) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics")
                        .cookie(access)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"name\":\"Clinic %s\",\"timezone\":\"UTC\"}",
                                UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "id");
    }

    private String invite(String clinicId, Cookie admin, String email, String role) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics/" + clinicId + "/staff")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","fullName":"Staff","role":"%s"}
                                """, email, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "membershipId");
    }

    private void setPassword(String email, String code) throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","code":"%s","password":"%s","confirmPassword":"%s"}
                                """, email, code, PASSWORD, PASSWORD)))
                .andExpect(status().isNoContent());
    }

    private Cookie loginAfterInvite(String email) throws Exception {
        setPassword(email, lastCodeSentTo(email));
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"%s\"}", email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Cookie("cp_access", cookieValue(login, "cp_access"));
    }

    private String lastCodeSentTo(String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), body.capture());
        String last = body.getAllValues().get(body.getAllValues().size() - 1);
        Matcher m = CODE_IN_BODY.matcher(last);
        assertThat(m.find()).as("OTP present in mail body").isTrue();
        return m.group(1);
    }

    private String jsonField(String json, String field) {
        return json.replaceAll(".*\"" + field + "\":\"([^\"]+)\".*", "$1");
    }

    private String body(String template, Object... args) {
        return String.format(template, args);
    }

    private String cookieValue(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .map(h -> h.substring(h.indexOf('=') + 1, h.indexOf(';')))
                .orElse(null);
    }
}
