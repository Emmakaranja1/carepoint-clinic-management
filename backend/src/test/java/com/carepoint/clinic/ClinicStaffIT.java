package com.carepoint.clinic;

import static com.carepoint.support.StaffFixtures.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.carepoint.clinic.application.ClinicProvisioningService;
import com.carepoint.clinic.application.ClinicProvisioningService.AdminProvisioning;
import com.carepoint.common.mail.MailService;
import com.carepoint.identity.application.GoogleIdentityVerifier;
import com.carepoint.identity.persistence.ClinicMembershipRepository;
import com.carepoint.support.StaffFixtures;

/** Clinic admin workspace: provisioning, staff invitations, portal login, isolation, audit. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000",
        "carepoint.rate-limit.staff-invite-per-minute=1000"})
class ClinicStaffIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ClinicProvisioningService provisioning;

    @Autowired
    ClinicMembershipRepository memberships;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    MailService mail;

    @MockitoBean
    GoogleIdentityVerifier google;

    // ---------- provisioning ----------

    @Test
    void publicClinicCreation_isGone_patientCannotBecomeAdmin() throws Exception {
        Cookie patient = verifiedSession(email("owner"));
        mutate(post("/api/v1/clinics"), patient, "{\"name\":\"Mine\",\"timezone\":\"UTC\"}")
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(get("/api/v1/clinics/mine").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bootstrapAdmin_isIdempotent_andActivatesThroughInvitation() throws Exception {
        UUID clinicId = provisioning.createClinic("Boot " + sfx(), "Africa/Nairobi", null).getId();
        String adminEmail = email("boot");

        assertThat(provisioning.ensureAdmin(clinicId, adminEmail, "Boot Admin"))
                .isEqualTo(AdminProvisioning.INVITED);
        assertThat(provisioning.ensureAdmin(clinicId, adminEmail, "Boot Admin"))
                .isEqualTo(AdminProvisioning.INVITATION_PENDING);

        StaffFixtures.acceptInvite(mvc, mail, adminEmail);
        assertThat(provisioning.ensureAdmin(clinicId, adminEmail, "Boot Admin"))
                .isEqualTo(AdminProvisioning.ALREADY_ACTIVE);

        adminLogin(adminEmail, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicRoles." + clinicId).value("CLINIC_ADMIN"));
    }

    // ---------- clinic-admin portal login ----------

    @Test
    void adminLogin_rejectsPatientReceptionistAndWrongPassword_withoutSession() throws Exception {
        Cookie admin = verifiedSession(email("al"));
        String clinicId = createClinicAs(admin);
        String patientEmail = email("alp");
        verifiedSession(patientEmail);
        String recEmail = email("alr");
        invite(clinicId, admin, recEmail, "RECEPTIONIST");
        StaffFixtures.acceptInvite(mvc, mail, recEmail);

        for (String denied : new String[] {patientEmail, recEmail}) {
            MvcResult r = adminLogin(denied, PASSWORD)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andReturn();
            assertThat(r.getResponse().getHeaders("Set-Cookie")).isEmpty();
        }
        adminLogin(patientEmail, "Wrong-Pass-1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void adminLogin_disabledAdmin_denied() throws Exception {
        Cookie founder = verifiedSession(email("da"));
        String clinicId = createClinicAs(founder);
        String second = email("da2");
        String membership = invite(clinicId, founder, second, "CLINIC_ADMIN");
        StaffFixtures.acceptInvite(mvc, mail, second);
        adminLogin(second, PASSWORD).andExpect(status().isOk());

        mutate(patch(staffPath(clinicId) + "/" + membership), founder, "{\"status\":\"DISABLED\"}")
                .andExpect(status().isOk());
        adminLogin(second, PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    void adminLogin_suspendedClinic_denied() throws Exception {
        String adminEmail = email("sus");
        Cookie admin = verifiedSession(adminEmail);
        String clinicId = createClinicAs(admin);
        adminLogin(adminEmail, PASSWORD).andExpect(status().isOk());

        jdbc.update("UPDATE clinics SET status = 'DISABLED' WHERE id = ?::uuid", clinicId);
        adminLogin(adminEmail, PASSWORD).andExpect(status().isForbidden());
    }

    // ---------- authz matrix ----------

    @Test
    void patient_adminEndpoints_403() throws Exception {
        String clinicId = createClinicAs(verifiedSession(email("a")));
        Cookie patient = verifiedSession(email("p"));
        for (String path : adminReadPaths(clinicId)) {
            mvc.perform(get(path).cookie(patient))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void anonymous_adminEndpoints_401() throws Exception {
        String clinicId = createClinicAs(verifiedSession(email("anon")));
        for (String path : adminReadPaths(clinicId)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void adminOfOtherClinic_404_notLeaked() throws Exception {
        String clinicA = createClinicAs(verifiedSession(email("sa")));
        Cookie adminB = verifiedSession(email("sb"));
        createClinicAs(adminB);

        for (String path : adminReadPaths(clinicA)) {
            mvc.perform(get(path).cookie(adminB))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        mutate(post(staffPath(clinicA)), adminB, staffBody(email("x"), "RECEPTIONIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void receptionist_cannotInviteOrOpenAdminWorkspace() throws Exception {
        Cookie admin = verifiedSession(email("radm"));
        String clinicId = createClinicAs(admin);
        String recEmail = email("recep");
        invite(clinicId, admin, recEmail, "RECEPTIONIST");
        Cookie recep = loginAfterInvite(recEmail);

        mutate(post(staffPath(clinicId)), recep, staffBody(email("x"), "RECEPTIONIST"))
                .andExpect(status().isForbidden());
        mutate(post(staffPath(clinicId)), recep, staffBody(email("y"), "CLINIC_ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/admin/overview").cookie(recep))
                .andExpect(status().isForbidden());
    }

    @Test
    void invite_ignoresClinicIdInBody_pathClinicWins() throws Exception {
        Cookie adminA = verifiedSession(email("ma"));
        String clinicA = createClinicAs(adminA);
        Cookie adminB = verifiedSession(email("mb"));
        String clinicB = createClinicAs(adminB);
        String staff = email("mass");

        mutate(post(staffPath(clinicA)), adminA, """
                {"email":"%s","fullName":"Mass","role":"RECEPTIONIST","clinicId":"%s","status":"ACTIVE"}
                """.formatted(staff, clinicB))
                .andExpect(status().isCreated());

        mvc.perform(get(staffPath(clinicB) + "?q=" + staff).cookie(adminB))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(staffPath(clinicA) + "?q=" + staff).cookie(adminA))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------- invitations ----------

    @Test
    void invite_newReceptionist_pendingUntilAccepted_thenActive() throws Exception {
        Cookie admin = verifiedSession(email("inv"));
        String clinicId = createClinicAs(admin);
        String staffEmail = email("rc");

        mutate(post(staffPath(clinicId)), admin, staffBody(staffEmail, "RECEPTIONIST"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.invitation").value("PENDING"))
                .andExpect(jsonPath("$.invitationExpiresAt").isString());

        String token = StaffFixtures.lastInviteTokenSentTo(mail, staffEmail);
        mutate(post("/api/v1/invitations/preview"), null, "{\"token\":\"" + token + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(staffEmail))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));

        StaffFixtures.acceptInvite(mvc, mail, staffEmail);
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(staffEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicRoles." + clinicId).value("RECEPTIONIST"));

        mvc.perform(get(staffPath(clinicId) + "?q=" + staffEmail).cookie(admin))
                .andExpect(jsonPath("$.content[0].invitation").value("ACCEPTED"));
    }

    @Test
    void invitationToken_isSingleUse() throws Exception {
        Cookie admin = verifiedSession(email("su"));
        String clinicId = createClinicAs(admin);
        String staffEmail = email("su-rc");
        invite(clinicId, admin, staffEmail, "RECEPTIONIST");
        String token = StaffFixtures.lastInviteTokenSentTo(mail, staffEmail);

        accept(token).andExpect(status().isNoContent());
        accept(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void invitationToken_expiredOrUnknown_rejected() throws Exception {
        Cookie admin = verifiedSession(email("ex"));
        String clinicId = createClinicAs(admin);
        String staffEmail = email("ex-rc");
        String membership = invite(clinicId, admin, staffEmail, "RECEPTIONIST");
        String token = StaffFixtures.lastInviteTokenSentTo(mail, staffEmail);

        accept("not-a-real-token").andExpect(jsonPath("$.code").value("INVITATION_INVALID"));

        jdbc.update("""
                UPDATE staff_invitations SET created_at = now() - interval '4 days',
                       expires_at = now() - interval '1 second'
                WHERE membership_id = ?::uuid
                """, membership);
        accept(token).andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
        mvc.perform(get(staffPath(clinicId) + "/" + membership).cookie(admin))
                .andExpect(jsonPath("$.invitation").value("EXPIRED"));
    }

    @Test
    void resend_revokesPreviousLink_andIsRejectedAfterAcceptance() throws Exception {
        Cookie admin = verifiedSession(email("rs"));
        String clinicId = createClinicAs(admin);
        String staffEmail = email("rs-rc");
        String membership = invite(clinicId, admin, staffEmail, "RECEPTIONIST");
        String first = StaffFixtures.lastInviteTokenSentTo(mail, staffEmail);

        mutate(post(staffPath(clinicId) + "/" + membership + "/invitation"), admin, null)
                .andExpect(status().isNoContent());
        String second = StaffFixtures.lastInviteTokenSentTo(mail, staffEmail);
        assertThat(second).isNotEqualTo(first);

        accept(first).andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
        accept(second).andExpect(status().isNoContent());

        mutate(post(staffPath(clinicId) + "/" + membership + "/invitation"), admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.membership").isString());
    }

    @Test
    void deactivatedBeforeAcceptance_invitationUnusable() throws Exception {
        Cookie admin = verifiedSession(email("dba"));
        String clinicId = createClinicAs(admin);
        String staffEmail = email("dba-rc");
        String membership = invite(clinicId, admin, staffEmail, "RECEPTIONIST");
        String token = StaffFixtures.lastInviteTokenSentTo(mail, staffEmail);

        mutate(patch(staffPath(clinicId) + "/" + membership), admin, "{\"status\":\"DISABLED\"}")
                .andExpect(status().isOk());
        accept(token).andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void invite_duplicateMember_rejected() throws Exception {
        Cookie admin = verifiedSession(email("dup"));
        String clinicId = createClinicAs(admin);
        String staffEmail = email("dup-staff");
        verifiedSession(staffEmail);

        invite(clinicId, admin, staffEmail, "RECEPTIONIST");
        mutate(post(staffPath(clinicId)), admin, staffBody(staffEmail, "DOCTOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").isString());
    }

    @Test
    void invite_invalidInput_400() throws Exception {
        Cookie admin = verifiedSession(email("val"));
        String clinicId = createClinicAs(admin);
        mutate(post(staffPath(clinicId)), admin,
                "{\"email\":\"nope\",\"fullName\":\"\",\"role\":\"SUPERUSER\",\"phone\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").isString())
                .andExpect(jsonPath("$.fields.fullName").isString())
                .andExpect(jsonPath("$.fields.phone").isString());
        mutate(post(staffPath(clinicId)), admin, staffBody(email("r"), "SUPERUSER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").isString());
    }

    // ---------- staff management ----------

    @Test
    void deactivatedReceptionist_losesStaffAccessImmediately() throws Exception {
        Cookie admin = verifiedSession(email("deact"));
        String clinicId = createClinicAs(admin);
        String recEmail = email("deact-rc");
        String membership = invite(clinicId, admin, recEmail, "RECEPTIONIST");
        Cookie recep = loginAfterInvite(recEmail);
        mvc.perform(get(staffPath(clinicId)).cookie(recep)).andExpect(status().isOk());

        mutate(patch(staffPath(clinicId) + "/" + membership), admin, "{\"status\":\"DISABLED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(get(staffPath(clinicId)).cookie(recep)).andExpect(status().isForbidden());
    }

    @Test
    void updateStaff_selfModification_rejected() throws Exception {
        Cookie admin = verifiedSession(email("self"));
        String clinicId = createClinicAs(admin);
        String body = mvc.perform(get(staffPath(clinicId)).cookie(admin))
                .andReturn().getResponse().getContentAsString();
        String membershipId = jsonField(body, "membershipId");

        mutate(patch(staffPath(clinicId) + "/" + membershipId), admin, "{\"status\":\"DISABLED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.membership").isString());
    }

    @Test
    void updateStaff_membershipOfOtherClinic_404() throws Exception {
        Cookie adminA = verifiedSession(email("xa"));
        String clinicA = createClinicAs(adminA);
        String foreign = invite(clinicA, adminA, email("fm"), "RECEPTIONIST");

        Cookie adminB = verifiedSession(email("xb"));
        String clinicB = createClinicAs(adminB);
        mutate(patch(staffPath(clinicB) + "/" + foreign), adminB, "{\"role\":\"CLINIC_ADMIN\"}")
                .andExpect(status().isNotFound());
        mvc.perform(get(staffPath(clinicB) + "/" + foreign).cookie(adminB))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffList_paginatesAndFilters() throws Exception {
        Cookie admin = verifiedSession(email("pg"));
        String clinicId = createClinicAs(admin);
        for (int i = 0; i < 3; i++) {
            invite(clinicId, admin, email("pg-rc" + i), "RECEPTIONIST");
        }
        mvc.perform(get(staffPath(clinicId) + "?size=2").cookie(admin))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get(staffPath(clinicId) + "?role=RECEPTIONIST").cookie(admin))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(staffPath(clinicId) + "?size=500").cookie(admin))
                .andExpect(jsonPath("$.size").value(50));
        mvc.perform(get(staffPath(clinicId) + "?q=%25").cookie(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void overviewAndActivity_reflectRealStaffChanges() throws Exception {
        Cookie admin = verifiedSession(email("ov"));
        String clinicId = createClinicAs(admin);
        String membership = invite(clinicId, admin, email("ov-rc"), "RECEPTIONIST");
        mutate(patch(staffPath(clinicId) + "/" + membership), admin, "{\"status\":\"DISABLED\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/admin/overview").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinic.id").value(clinicId))
                .andExpect(jsonPath("$.staff.total").value(2))
                .andExpect(jsonPath("$.staff.active").value(1))
                .andExpect(jsonPath("$.staff.disabled").value(1))
                .andExpect(jsonPath("$.staff.activeByRole.CLINIC_ADMIN").value(1));

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/admin/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].action").value("STAFF_STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[0].to").value("DISABLED"))
                .andExpect(jsonPath("$.content[0].targetName").value("Staff"));
    }

    // ---------- helpers ----------

    private String[] adminReadPaths(String clinicId) {
        return new String[] {
                "/api/v1/clinics/" + clinicId + "/admin/overview",
                "/api/v1/clinics/" + clinicId + "/admin/activity",
                staffPath(clinicId)};
    }

    private ResultActions mutate(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                 Cookie cookie, String body) throws Exception {
        req.header("X-Requested-With", "XMLHttpRequest");
        if (cookie != null) {
            req.cookie(cookie);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private ResultActions adminLogin(String email, String password) throws Exception {
        return mutate(post("/api/v1/auth/clinic-admin/login"), null, loginBody(email, password));
    }

    private ResultActions accept(String token) throws Exception {
        return mutate(post("/api/v1/invitations/accept"), null, """
                {"token":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(token, PASSWORD, PASSWORD));
    }

    private Cookie verifiedSession(String email) throws Exception {
        mutate(post("/api/v1/auth/register"), null, """
                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"T"}
                """.formatted(email, PASSWORD, PASSWORD))
                .andExpect(status().isCreated());
        return login(email);
    }

    private Cookie login(String email) throws Exception {
        MvcResult r = mutate(post("/api/v1/auth/login"), null, loginBody(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        return new Cookie("cp_access", cookieValue(r, "cp_access"));
    }

    private Cookie loginAfterInvite(String email) throws Exception {
        StaffFixtures.acceptInvite(mvc, mail, email);
        return login(email);
    }

    private String createClinicAs(Cookie access) throws Exception {
        return StaffFixtures.createClinicAs(mvc, provisioning, memberships, access);
    }

    private String invite(String clinicId, Cookie admin, String email, String role) throws Exception {
        MvcResult r = mutate(post(staffPath(clinicId)), admin, staffBody(email, role))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "membershipId");
    }

    private static String staffPath(String clinicId) {
        return "/api/v1/clinics/" + clinicId + "/staff";
    }

    private static String staffBody(String email, String role) {
        return "{\"email\":\"%s\",\"fullName\":\"Staff\",\"role\":\"%s\"}".formatted(email, role);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String email(String prefix) {
        return prefix + "-" + sfx() + "@test.local";
    }

    private static String sfx() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String jsonField(String json, String field) {
        return json.replaceAll(".*\"" + field + "\":\"([^\"]+)\".*", "$1");
    }

    private static String cookieValue(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .map(h -> h.substring(h.indexOf('=') + 1, h.indexOf(';')))
                .orElse(null);
    }
}
