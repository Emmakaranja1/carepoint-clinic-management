package com.carepoint.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000"})
class AvailabilityIT {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final Pattern CODE_IN_BODY = Pattern.compile("is: (\\d{6})");
    private static final LocalDate MONDAY = LocalDate.now(ZoneOffset.UTC)
            .plusWeeks(1).with(DayOfWeek.MONDAY);

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
    JdbcTemplate jdbc;

    @MockitoBean
    MailService mail;

    UUID specialty;
    Cookie admin;
    String adminEmail;
    String clinicId;
    String doctorId;
    String doctorEmail;
    String availability;

    @BeforeEach
    void setup() throws Exception {
        specialty = specialties.save(new Specialty("S-" + sfx())).getId();
        adminEmail = "av-" + sfx() + "@test.local";
        admin = verifiedSession(adminEmail);
        clinicId = createClinicAs(admin);
        doctorEmail = "d-" + sfx() + "@test.local";
        String membershipId = invite(clinicId, admin, doctorEmail, "DOCTOR");
        doctorId = createProfile(clinicId, admin, membershipId);
        availability = "/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/availability";
        putSchedule(admin, doctorId, """
                {"windows":[{"dayOfWeek":0,"startTime":"09:00","endTime":"12:00","slotMinutes":30}]}
                """).andExpect(status().isOk());
    }

    // ---------- availability computation ----------

    @Test
    void availability_returnsGridSlots() throws Exception {
        // Mon 09:00–12:00 at 30-minute slots → 6 slots, UTC clinic
        mvc.perform(get(availability).param("from", MONDAY.toString())
                        .param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].start").value(MONDAY + "T09:00:00Z"))
                .andExpect(jsonPath("$[5].end").value(MONDAY + "T12:00:00Z"));
    }

    @Test
    void availability_subtractsTimeOff() throws Exception {
        addTimeOff(admin, doctorId,
                MONDAY + "T10:00:00Z", MONDAY + "T11:00:00Z")
                .andExpect(status().isCreated());

        // 10:00 and 10:30 slots are gone
        mvc.perform(get(availability).param("from", MONDAY.toString())
                        .param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[2].start").value(MONDAY + "T11:00:00Z"));
    }

    @Test
    void availability_subtractsBooked() throws Exception {
        UUID patientId = users.findByEmail(adminEmail).orElseThrow().getId();
        UUID doctorProfileId = UUID.fromString(doctorId);
        UUID clinicUuid = UUID.fromString(clinicId);
        jdbc.update("""
                INSERT INTO appointments
                  (id, clinic_id, doctor_id, patient_id, slot, created_by)
                VALUES (gen_random_uuid(), ?, ?, ?, tstzrange(?, ?, '[)'), ?)
                """,
                clinicUuid, doctorProfileId, patientId,
                java.sql.Timestamp.from(MONDAY.atTime(9, 30).toInstant(ZoneOffset.UTC)),
                java.sql.Timestamp.from(MONDAY.atTime(10, 0).toInstant(ZoneOffset.UTC)),
                patientId);

        mvc.perform(get(availability).param("from", MONDAY.toString())
                        .param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[1].start").value(MONDAY + "T10:00:00Z"));
    }

    @Test
    void availability_rangeValidation() throws Exception {
        mvc.perform(get(availability)
                        .param("from", MONDAY.minusWeeks(2).toString())
                        .param("to", MONDAY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.from").exists());
        mvc.perform(get(availability)
                        .param("from", MONDAY.toString())
                        .param("to", MONDAY.plusDays(40).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.to").exists());
        mvc.perform(get(availability)
                        .param("from", MONDAY.plusDays(1).toString())
                        .param("to", MONDAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void availability_crossClinic_404() throws Exception {
        Cookie other = verifiedSession("x-" + sfx() + "@test.local");
        String otherClinic = createClinicAs(other);
        mvc.perform(get("/api/v1/clinics/" + otherClinic + "/doctors/" + doctorId
                        + "/availability")
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isNotFound());
    }

    // ---------- schedule management ----------

    @Test
    void schedule_overlappingWindows_400() throws Exception {
        putSchedule(admin, doctorId, """
                {"windows":[
                  {"dayOfWeek":0,"startTime":"09:00","endTime":"12:00","slotMinutes":30},
                  {"dayOfWeek":0,"startTime":"11:00","endTime":"13:00","slotMinutes":30}]}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.windows").exists());
    }

    @Test
    void schedule_doctorSelfCanEdit_otherDoctor403() throws Exception {
        Cookie doctorSelf = loginAfterInvite(doctorEmail);
        putSchedule(doctorSelf, doctorId, """
                {"windows":[{"dayOfWeek":1,"startTime":"13:00","endTime":"17:00","slotMinutes":60}]}
                """).andExpect(status().isOk());

        // a different doctor may not touch it
        String otherMembership = invite(clinicId, admin, "d2-" + sfx() + "@test.local", "DOCTOR");
        String otherDoctor = createProfile(clinicId, admin, otherMembership);
        putSchedule(doctorSelf, otherDoctor, """
                {"windows":[{"dayOfWeek":1,"startTime":"13:00","endTime":"14:00","slotMinutes":60}]}
                """).andExpect(status().isForbidden());
    }

    @Test
    void timeOff_crudRoundtrip() throws Exception {
        MvcResult created = addTimeOff(admin, doctorId,
                MONDAY.plusWeeks(2) + "T08:00:00Z", MONDAY.plusWeeks(2) + "T18:00:00Z")
                .andExpect(status().isCreated())
                .andReturn();
        String offId = jsonField(created.getResponse().getContentAsString(), "id");

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/time-off")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(offId));

        mvc.perform(delete("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId
                        + "/time-off/" + offId)
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/time-off")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    private String sfx() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

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
                        .content(body("{\"name\":\"Clinic %s\",\"timezone\":\"UTC\"}", sfx())))
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
                                {"email":"%s","fullName":"Dr T","role":"%s"}
                                """, email, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "membershipId");
    }

    private String createProfile(String clinicId, Cookie admin, String membershipId) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"membershipId":"%s","specialtyId":"%s","consultMinutes":30}
                                """, membershipId, specialty)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "id");
    }

    private org.springframework.test.web.servlet.ResultActions putSchedule(
            Cookie cookie, String doctorId, String body) throws Exception {
        return mvc.perform(put("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/schedule")
                .cookie(cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions addTimeOff(
            Cookie cookie, String doctorId, String from, String to) throws Exception {
        return mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/time-off")
                .cookie(cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("{\"offFrom\":\"%s\",\"offTo\":\"%s\",\"reason\":\"test\"}", from, to)));
    }

    private Cookie loginAfterInvite(String email) throws Exception {
        String code = lastCodeSentTo(email);
        mvc.perform(post("/api/v1/auth/reset-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email":"%s","code":"%s","password":"%s","confirmPassword":"%s"}
                                """, email, code, PASSWORD, PASSWORD)))
                .andExpect(status().isNoContent());
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
