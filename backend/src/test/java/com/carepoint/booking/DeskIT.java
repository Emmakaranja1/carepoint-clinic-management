package com.carepoint.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** Receptionist desk + doctor agenda (docs/01 §5.6 US-R1..R3, US-S3). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000",
        "carepoint.rate-limit.availability-per-minute=1000",
        "carepoint.rate-limit.booking-per-minute=1000"})
class DeskIT {

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
    String agenda;
    String deskBook;

    @BeforeEach
    void setup() throws Exception {
        UUID specialty = specialties.save(new Specialty("S-" + sfx())).getId();
        admin = verifiedSession("dk-admin-" + sfx() + "@test.local");
        patientEmail = "dk-pt-" + sfx() + "@test.local";
        patient = verifiedSession(patientEmail);
        clinicId = createClinicAs(admin);

        String docEmail = "dk-dr-" + sfx() + "@test.local";
        String docMembership = invite(clinicId, admin, docEmail, "DOCTOR");
        doctorId = createProfile(clinicId, admin, docMembership, specialty);

        String recEmail = "dk-rc-" + sfx() + "@test.local";
        invite(clinicId, admin, recEmail, "RECEPTIONIST");
        receptionist = loginAfterInvite(recEmail);

        agenda = "/api/v1/clinics/" + clinicId + "/appointments";
        deskBook = agenda;

        mvc.perform(put("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/schedule")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"windows":[{"dayOfWeek":0,"startTime":"09:00","endTime":"12:00","slotMinutes":30}]}
                                """))
                .andExpect(status().isOk());
    }

    // ---------- agenda ----------

    @Test
    void agenda_listsWithNamesAndFilters() throws Exception {
        bookAsPatient(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");
        deskBook(receptionist, bookForGuestBody("walkin-" + sfx() + "@test.local",
                MONDAY + "T10:00:00Z", MONDAY + "T10:30:00Z"))
                .andExpect(status().isCreated());

        mvc.perform(get(agenda).cookie(receptionist)
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].start").value(MONDAY + "T09:00:00Z"))
                .andExpect(jsonPath("$.content[0].patientName").value("T"))
                .andExpect(jsonPath("$.content[0].doctorName").value("Dr T"))
                .andExpect(jsonPath("$.content[1].bookedForGuest").value(true));

        mvc.perform(get(agenda).cookie(receptionist)
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString())
                        .param("status", "BOOKED")
                        .param("doctorId", doctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get(agenda).cookie(receptionist)
                        .param("from", MONDAY.plusDays(1).toString())
                        .param("to", MONDAY.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get(agenda).cookie(receptionist)
                        .param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agenda_authzMatrix() throws Exception {
        // patient → 403
        mvc.perform(get(agenda).cookie(patient))
                .andExpect(status().isForbidden());
        // staff of a different clinic → 404 (existence not leaked)
        Cookie other = verifiedSession("oth-" + sfx() + "@test.local");
        String otherClinic = createClinicAs(other);
        mvc.perform(get(agenda).cookie(other))
                .andExpect(status().isNotFound());
        // a DOCTOR member may not read the clinic-wide agenda (receptionist+admin only)
        String doc2 = "dk-dr2-" + sfx() + "@test.local";
        invite(clinicId, admin, doc2, "DOCTOR");
        Cookie doctorCookie = loginAfterInvite(doc2);
        mvc.perform(get(agenda).cookie(doctorCookie))
                .andExpect(status().isForbidden());
        // anonymous → 401
        mvc.perform(get(agenda))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myAgenda_doctorSeesOwnDay() throws Exception {
        bookAsPatient(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");
        String docEmail = jdbc.queryForObject("""
                SELECT u.email::text FROM users u
                JOIN clinic_memberships m ON m.user_id = u.id
                JOIN doctor_profiles dp ON dp.membership_id = m.id
                WHERE dp.id = ?::uuid
                """, String.class, doctorId);
        Cookie doctor = loginAfterInvite(docEmail);

        mvc.perform(get("/api/v1/clinics/" + clinicId + "/me/appointments")
                        .cookie(doctor).param("date", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("T"));

        // receptionist has no doctor profile → 403
        mvc.perform(get("/api/v1/clinics/" + clinicId + "/me/appointments")
                        .cookie(receptionist))
                .andExpect(status().isForbidden());
    }

    // ---------- book on behalf ----------

    @Test
    void bookOnBehalf_existingPatient() throws Exception {
        UUID patientId = users.findByEmail(patientEmail).orElseThrow().getId();
        deskBook(receptionist, """
                {"doctorId":"%s","start":"%s","end":"%s","patientId":"%s","reason":"phone booking"}
                """.formatted(doctorId, MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z", patientId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.bookedForGuest").value(false));

        // and it shows in the patient's own list
        mvc.perform(get("/api/v1/appointments/mine").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void bookOnBehalf_inlineGuest_createsClaimableRecord() throws Exception {
        String guestEmail = "guest-" + sfx() + "@test.local";
        MvcResult r = deskBook(receptionist,
                bookForGuestBody(guestEmail, MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookedForGuest").value(true))
                .andReturn();
        String apptId = jsonField(r.getResponse().getContentAsString(), "id");

        assertThat(users.findByEmail(guestEmail).orElseThrow().isGuest()).isTrue();

        // guest claims the record via normal registration → login
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"Claimed"}
                        """.formatted(guestEmail, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult verify = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(guestEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie claimed = new Cookie("cp_access", cookieValue(verify, "cp_access"));

        mvc.perform(get("/api/v1/appointments/mine").cookie(claimed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(apptId));
    }

    @Test
    void bookOnBehalf_patientSelectionValidation() throws Exception {
        // both patientId and inline patient → 400
        deskBook(receptionist, """
                {"doctorId":"%s","start":"%s","end":"%s","patientId":"%s",
                 "patient":{"email":"g@t.local","fullName":"G"}}
                """.formatted(doctorId, MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z",
                UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        // neither → 400
        deskBook(receptionist, """
                {"doctorId":"%s","start":"%s","end":"%s"}
                """.formatted(doctorId, MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z"))
                .andExpect(status().isBadRequest());
        // patient may not use the desk endpoint
        deskBook(patient, bookForGuestBody("g2-" + sfx() + "@t.local",
                MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookOnBehalf_idempotencyKey_replaysAndConflicts() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = bookForGuestBody("idem-" + sfx() + "@t.local",
                MONDAY + "T10:00:00Z", MONDAY + "T10:30:00Z");

        MvcResult first = deskBook(receptionist, body, key)
                .andExpect(status().isCreated())
                .andReturn();
        String id = jsonField(first.getResponse().getContentAsString(), "id");

        // same key + same request → replays the original, no duplicate
        deskBook(receptionist, body, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(agenda).cookie(receptionist)
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        // same key + different request → conflict
        deskBook(receptionist, bookForGuestBody("other-" + sfx() + "@t.local",
                        MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z"), key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // malformed header → 400, not a silent ignore
        deskBook(receptionist, body, "not-a-uuid")
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookOnBehalf_sameSlot_conflict() throws Exception {
        bookAsPatient(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");
        // committed booking → slot absent from availability → INVALID_SLOT
        // (SLOT_TAKEN is reserved for the insert-race path)
        deskBook(receptionist, bookForGuestBody("g3-" + sfx() + "@t.local",
                MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));
    }

    // ---------- transitions ----------

    @Test
    void transition_cancelBypassesCutoff() throws Exception {
        // 12h out — inside the 24h patient cutoff, but staff may still cancel
        String id = insertBooked(12);
        mvc.perform(patch(agenda + "/" + id).cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void transition_terminalAndTimingRules() throws Exception {
        String pastId = insertBooked(-2);   // started 2h ago
        String futureId = insertBooked(48); // 2 days out

        // COMPLETED only after start
        mvc.perform(patch(agenda + "/" + futureId).cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS_TRANSITION"));

        mvc.perform(patch(agenda + "/" + pastId).cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // COMPLETED is terminal
        mvc.perform(patch(agenda + "/" + pastId).cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NO_SHOW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS_TRANSITION"));

        // unknown target → 400
        mvc.perform(patch(agenda + "/" + futureId).cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOOKED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transition_noShowAfterStart() throws Exception {
        String id = insertBooked(-1);
        mvc.perform(patch(agenda + "/" + id).cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NO_SHOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void transition_crossClinic_404() throws Exception {
        String id = insertBooked(48);
        Cookie other = verifiedSession("xcl-" + sfx() + "@test.local");
        createClinicAs(other); // staff elsewhere, but not this clinic
        mvc.perform(patch(agenda + "/" + id).cookie(other)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- reschedule ----------

    @Test
    void reschedule_atomicFreesOldBooksNew() throws Exception {
        MvcResult r = bookAsPatient(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");
        String id = jsonField(r.getResponse().getContentAsString(), "id");

        mvc.perform(post(agenda + "/" + id + "/reschedule").cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"start":"%s","end":"%s"}
                        """.formatted(MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.start").value(MONDAY + "T11:00:00Z"));

        // old row is CANCELLED in the patient's list, new slot holds the booking
        mvc.perform(get("/api/v1/appointments/mine").cookie(patient).param("when", "upcoming"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].start").value(MONDAY + "T11:00:00Z"));
        mvc.perform(get("/api/v1/appointments/mine").cookie(patient).param("when", "past"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void reschedule_invalidSlot_keepsOriginal() throws Exception {
        MvcResult r = bookAsPatient(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");
        String id = jsonField(r.getResponse().getContentAsString(), "id");

        mvc.perform(post(agenda + "/" + id + "/reschedule").cookie(receptionist)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"start":"%s","end":"%s"}
                        """.formatted(MONDAY + "T13:00:00Z", MONDAY + "T13:30:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));

        // the original booking survived the failed reschedule
        mvc.perform(get("/api/v1/appointments/" + id).cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    // ---------- helpers ----------

    private String bookForGuestBody(String email, String start, String end) {
        return """
                {"doctorId":"%s","start":"%s","end":"%s",
                 "patient":{"email":"%s","fullName":"Walk In","phone":"0700"},
                 "reason":"walk-in"}
                """.formatted(doctorId, start, end, email);
    }

    private org.springframework.test.web.servlet.ResultActions deskBook(
            Cookie who, String body) throws Exception {
        return deskBook(who, body, null);
    }

    private org.springframework.test.web.servlet.ResultActions deskBook(
            Cookie who, String body, String idempotencyKey) throws Exception {
        var req = post(deskBook).cookie(who)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            req.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(req);
    }

    private MvcResult bookAsPatient(String start, String end) throws Exception {
        return mvc.perform(post("/api/v1/appointments").cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"clinicId":"%s","doctorId":"%s","start":"%s","end":"%s","reason":"x"}
                        """.formatted(clinicId, doctorId, start, end)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Direct insert for states unreachable through the API (past slots). */
    private String insertBooked(int hoursFromNow) {
        UUID patientId = users.findByEmail(patientEmail).orElseThrow().getId();
        return jdbc.queryForObject("""
                INSERT INTO appointments
                  (id, clinic_id, doctor_id, patient_id, slot, created_by)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?,
                        tstzrange(?, ?, '[)'), ?)
                RETURNING id::text
                """,
                String.class, clinicId, doctorId, patientId,
                java.sql.Timestamp.from(Instant.now().plusSeconds(hoursFromNow * 3600L)),
                java.sql.Timestamp.from(Instant.now().plusSeconds(hoursFromNow * 3600L + 1800)),
                patientId);
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
