package com.carepoint.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
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

/** Clinic summary report (docs/01 US-P1, docs/09 §2.6). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000",
        "carepoint.rate-limit.availability-per-minute=1000",
        "carepoint.rate-limit.booking-per-minute=1000"})
class ReportIT {

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

    Cookie admin;
    Cookie receptionist;
    Cookie patient;
    String patientEmail;
    String clinicId;
    String doctorId;
    String summary;

    @BeforeEach
    void setup() throws Exception {
        UUID specialty = specialties.save(new Specialty("S-" + sfx())).getId();
        admin = verifiedSession("rp-admin-" + sfx() + "@test.local");
        patientEmail = "rp-pt-" + sfx() + "@test.local";
        patient = verifiedSession(patientEmail);
        clinicId = createClinicAs(admin);

        String docEmail = "rp-dr-" + sfx() + "@test.local";
        String docMembership = invite(clinicId, admin, docEmail, "DOCTOR");
        doctorId = createProfile(clinicId, admin, docMembership, specialty);

        String recEmail = "rp-rc-" + sfx() + "@test.local";
        invite(clinicId, admin, recEmail, "RECEPTIONIST");
        receptionist = loginAfterInvite(recEmail);

        summary = "/api/v1/clinics/" + clinicId + "/reports/summary";

        // Mon 09:00-12:00 at 30 min → capacity 6 per Monday in range
        mvc.perform(put("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/schedule")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"windows":[{"dayOfWeek":0,"startTime":"09:00","endTime":"12:00","slotMinutes":30}]}
                                """))
                .andExpect(status().isOk());
    }

    private void insertAppointment(String status, Instant start, Instant end) {
        UUID patientId = users.findByEmail(patientEmail).orElseThrow().getId();
        jdbc.update("""
                INSERT INTO appointments
                  (id, clinic_id, doctor_id, patient_id, slot, status, created_by)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?,
                        tstzrange(?, ?, '[)'), ?, ?)
                """,
                clinicId, doctorId, patientId,
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end),
                status, patientId);
    }

    @Test
    void summary_statusCountsAndNoShowRate() throws Exception {
        Instant past = Instant.now().minusSeconds(86400 * 3);
        insertAppointment("COMPLETED", past, past.plusSeconds(1800));
        insertAppointment("COMPLETED", past.plusSeconds(3600), past.plusSeconds(5400));
        insertAppointment("NO_SHOW", past.plusSeconds(7200), past.plusSeconds(9000));
        bookAsPatient(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");

        // range covering everything (past 3d through next Monday)
        mvc.perform(get(summary).cookie(receptionist)
                        .param("from", LocalDate.now(ZoneOffset.UTC).minusDays(4).toString())
                        .param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.BOOKED").value(1))
                .andExpect(jsonPath("$.byStatus.COMPLETED").value(2))
                .andExpect(jsonPath("$.byStatus.NO_SHOW").value(1))
                .andExpect(jsonPath("$.byStatus.CANCELLED").value(0))
                .andExpect(jsonPath("$.total").value(4))
                // noShowRate = 1 / (2 completed + 1 no-show)
                .andExpect(jsonPath("$.noShowRate").value(0.3333333333333333));
    }

    @Test
    void summary_perDoctorCapacityAndUtilization() throws Exception {
        bookAsPatient(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");
        bookAsPatient(MONDAY + "T09:30:00Z", MONDAY + "T10:00:00Z");

        mvc.perform(get(summary).cookie(receptionist)
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perDoctor.length()").value(1))
                .andExpect(jsonPath("$.perDoctor[0].doctorId").value(doctorId))
                .andExpect(jsonPath("$.perDoctor[0].doctorName").value("Dr T"))
                .andExpect(jsonPath("$.perDoctor[0].booked").value(2))
                .andExpect(jsonPath("$.perDoctor[0].capacity").value(6))
                .andExpect(jsonPath("$.perDoctor[0].utilization").value(0.3333333333333333));
    }

    @Test
    void summary_timeOffReducesCapacity() throws Exception {
        mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/time-off")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offFrom\":\"%s\",\"offTo\":\"%s\"}"
                                .formatted(MONDAY + "T09:00:00Z", MONDAY + "T11:00:00Z")))
                .andExpect(status().isCreated());

        mvc.perform(get(summary).cookie(receptionist)
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perDoctor[0].capacity").value(2)); // 11:00-12:00 only
    }

    @Test
    void summary_rangeValidation() throws Exception {
        mvc.perform(get(summary).cookie(receptionist)
                        .param("from", MONDAY.toString())
                        .param("to", MONDAY.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(summary).cookie(receptionist)
                        .param("from", "2020-01-01")
                        .param("to", "2021-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.to").value("Range is limited to 12 months"));
    }

    @Test
    void summary_authz() throws Exception {
        mvc.perform(get(summary).cookie(patient))
                .andExpect(status().isForbidden());
        mvc.perform(get(summary).cookie(receptionist))
                .andExpect(status().isOk());
        Cookie other = verifiedSession("rp-x-" + sfx() + "@test.local");
        createClinicAs(other);
        mvc.perform(get(summary).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get(summary))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_emptyClinic_zeroes() throws Exception {
        Cookie fresh = verifiedSession("rp-f-" + sfx() + "@test.local");
        String emptyClinic = createClinicAs(fresh);
        mvc.perform(get("/api/v1/clinics/" + emptyClinic + "/reports/summary")
                        .cookie(fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.noShowRate").value(0.0))
                .andExpect(jsonPath("$.perDoctor.length()").value(0));
    }

    // ---------- helpers ----------

    private void bookAsPatient(String start, String end) throws Exception {
        mvc.perform(post("/api/v1/appointments").cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"clinicId":"%s","doctorId":"%s","start":"%s","end":"%s"}
                        """.formatted(clinicId, doctorId, start, end)))
                .andExpect(status().isCreated());
    }

    private String sfx() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Cookie verifiedSession(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"T"}
                        """.formatted(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Cookie("cp_access", cookieValue(r, "cp_access"));
    }

    private String createClinicAs(Cookie access) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics").cookie(access)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clinic %s\",\"timezone\":\"UTC\"}".formatted(sfx())))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "id");
    }

    private String invite(String clinicId, Cookie admin, String email, String role) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics/" + clinicId + "/staff")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","fullName":"Dr T","role":"%s"}
                        """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "membershipId");
    }

    private String createProfile(String clinicId, Cookie admin, String membershipId, UUID specialty)
            throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"membershipId":"%s","specialtyId":"%s","consultMinutes":30}
                        """.formatted(membershipId, specialty)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "id");
    }

    private Cookie loginAfterInvite(String email) throws Exception {
        String code = lastCodeSentTo(email);
        mvc.perform(post("/api/v1/auth/reset-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","code":"%s","password":"%s","confirmPassword":"%s"}
                        """.formatted(email, code, PASSWORD, PASSWORD)))
                .andExpect(status().isNoContent());
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
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

    private String cookieValue(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .map(h -> h.substring(h.indexOf('=') + 1, h.indexOf(';')))
                .orElse(null);
    }
}
