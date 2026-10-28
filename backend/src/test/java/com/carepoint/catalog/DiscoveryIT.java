package com.carepoint.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
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

import com.carepoint.catalog.domain.Specialty;
import com.carepoint.catalog.persistence.SpecialtyRepository;
import com.carepoint.common.mail.MailService;
import com.carepoint.identity.persistence.UserRepository;
import com.carepoint.media.domain.MediaObject;
import com.carepoint.media.persistence.MediaObjectRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000"})
class DiscoveryIT {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final Pattern CODE_IN_BODY = Pattern.compile("is: (\\d{6})");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    SpecialtyRepository specialties;

    @Autowired
    UserRepository users;

    @Autowired
    MediaObjectRepository media;

    @MockitoBean
    MailService mail;

    UUID cardiology;
    Cookie admin;
    String adminEmail;
    String clinicId;
    String doctorMembershipId;
    String doctorId;

    @BeforeEach
    void setup() throws Exception {
        cardiology = specialties.save(new Specialty("Cardiology-" + suffix())).getId();
        adminEmail = "disc-" + suffix() + "@test.local";
        admin = verifiedSession(adminEmail);
        clinicId = createClinicAs(admin);
        doctorMembershipId = invite(clinicId, admin, "dr-" + suffix() + "@test.local", "DOCTOR");
        doctorId = createProfile(clinicId, admin, doctorMembershipId, cardiology);
    }

    // ---------- public clinic directory ----------

    @Test
    void listClinics_public_noAuth() throws Exception {
        mvc.perform(get("/api/v1/clinics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listClinics_nameFilter() throws Exception {
        mvc.perform(get("/api/v1/clinics").param("name", clinicName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(clinicId));
        mvc.perform(get("/api/v1/clinics").param("name", "no-such-clinic-zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void clinicDetail_unknown_404() throws Exception {
        mvc.perform(get("/api/v1/clinics/" + clinicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clinicId));
        mvc.perform(get("/api/v1/clinics/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------- public doctor discovery ----------

    @Test
    void listDoctors_public_showsProfile() throws Exception {
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(doctorId))
                .andExpect(jsonPath("$.content[0].fullName").value("Staff"))
                .andExpect(jsonPath("$.content[0].specialty").exists());
    }

    @Test
    void listDoctors_specialtyAndNameFilters() throws Exception {
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors")
                        .param("specialtyId", cardiology.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors")
                        .param("specialtyId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors")
                        .param("q", "zzz-nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void doctorDetail_crossClinic_404() throws Exception {
        Cookie other = verifiedSession("oth-" + suffix() + "@test.local");
        String otherClinic = createClinicAs(other);

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Bio text"));
        mvc.perform(get("/api/v1/clinics/" + otherClinic + "/doctors/" + doctorId))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void clinicSpecialties_onlyUsedOnes() throws Exception {
        specialties.save(new Specialty("Dentistry-" + suffix())); // unused here
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    // ---------- staff profile writes ----------

    @Test
    void createProfile_receptionistMembership_rejected() throws Exception {
        String recMembership = invite(clinicId, admin, "rec-" + suffix() + "@test.local", "RECEPTIONIST");
        mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"membershipId":"%s","specialtyId":"%s"}
                                """, recMembership, cardiology)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.membershipId").isString());
    }

    @Test
    void createProfile_duplicateMembership_rejected() throws Exception {
        mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"membershipId":"%s","specialtyId":"%s"}
                                """, doctorMembershipId, cardiology)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.membershipId").isString());
    }

    @Test
    void createProfile_anonymous_401() throws Exception {
        mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"membershipId":"%s","specialtyId":"%s"}
                                """, doctorMembershipId, cardiology)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_changesBioAndSpecialty() throws Exception {
        UUID derm = specialties.save(new Specialty("Derm-" + suffix())).getId();
        mvc.perform(patch("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId)
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"specialtyId":"%s","bio":"New bio","consultMinutes":30}
                                """, derm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("New bio"))
                .andExpect(jsonPath("$.consultMinutes").value(30));
    }

    // ---------- media surfacing ----------

    @Test
    void discovery_surfacesActiveMediaOnly() throws Exception {
        UUID uploader = users.findByEmail(adminEmail).orElseThrow().getId();
        media.saveAndFlush(activeMedia("DOCTOR_PHOTO", "DOCTOR_PROFILE",
                UUID.fromString(doctorId), "active-doc-" + suffix() + ".png", uploader));
        media.saveAndFlush(activeMedia("CLINIC_LOGO", "CLINIC",
                UUID.fromString(clinicId), "active-logo-" + suffix() + ".png", uploader));
        // second doctor whose only upload never confirmed → not surfaced
        String m2 = invite(clinicId, admin, "dr2-" + suffix() + "@test.local", "DOCTOR");
        String d2 = createProfile(clinicId, admin, m2, cardiology);
        media.saveAndFlush(new MediaObject(UUID.fromString(clinicId), "DOCTOR_PHOTO",
                "public/" + clinicId + "/pending-" + suffix() + ".png", "local",
                "image/png", 10, "DOCTOR_PROFILE", UUID.fromString(d2), uploader));

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + doctorId + "')].photoUrl",
                        org.hamcrest.Matchers.contains(
                                org.hamcrest.Matchers.containsString("active-doc-"))))
                .andExpect(jsonPath("$.content[?(@.id=='" + d2 + "')].photoUrl",
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl")
                        .value(org.hamcrest.Matchers.containsString("active-doc-")));
        mvc.perform(get("/api/v1/clinics/" + clinicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl")
                        .value(org.hamcrest.Matchers.containsString("active-logo-")));
        mvc.perform(get("/api/v1/clinics").param("name", clinicName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].logoUrl")
                        .value(org.hamcrest.Matchers.containsString("active-logo-")));
    }

    private MediaObject activeMedia(String purpose, String ownerType, UUID ownerId,
                                    String fileName, UUID uploader) {
        MediaObject m = new MediaObject(UUID.fromString(clinicId), purpose,
                "public/" + clinicId + "/" + fileName, "local", "image/png", 10,
                ownerType, ownerId, uploader);
        m.markActive();
        return m;
    }

    // ---------- regressions ----------

    @Test
    void clinicsMine_stillRequiresAuth() throws Exception {
        mvc.perform(get("/api/v1/clinics/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- helpers ----------

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String clinicName;

    private Cookie verifiedSession(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"T"}
                                """, email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"%s\"}", email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Cookie("cp_access", cookieValue(r, "cp_access"));
    }

    private String createClinicAs(Cookie access) throws Exception {
        clinicName = "Clinic " + suffix();
        MvcResult r = mvc.perform(post("/api/v1/clinics")
                        .cookie(access)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"name\":\"%s\",\"timezone\":\"UTC\"}", clinicName)))
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

    private String createProfile(String clinicId, Cookie admin,
                                 String membershipId, UUID specialtyId) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"membershipId":"%s","specialtyId":"%s","bio":"Bio text","consultMinutes":20}
                                """, membershipId, specialtyId)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "id");
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
