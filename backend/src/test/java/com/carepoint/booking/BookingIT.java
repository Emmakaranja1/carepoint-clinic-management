package com.carepoint.booking;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/**
 * Patient booking (docs/01 US-B1..B3). The concurrency test exercises the
 * real path: every request re-validates the slot and races the partial
 * exclusion constraint — exactly one writer may win.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000",
        "carepoint.rate-limit.availability-per-minute=1000",
        "carepoint.rate-limit.booking-per-minute=1000"})
class BookingIT {

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
    Cookie patient;
    String patientEmail;
    UUID specialty;
    String clinicId;
    String doctorId;
    String availability;

    @BeforeEach
    void setup() throws Exception {
        specialty = specialties.save(new Specialty("S-" + sfx())).getId();
        admin = verifiedSession("bk-admin-" + sfx() + "@test.local");
        patientEmail = "bk-pt-" + sfx() + "@test.local";
        patient = verifiedSession(patientEmail);
        clinicId = createClinicAs(admin);
        String membershipId = invite(clinicId, admin, "bk-dr-" + sfx() + "@test.local", "DOCTOR");
        doctorId = createProfile(clinicId, admin, membershipId);
        availability = "/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/availability";
        mvc.perform(put("/api/v1/clinics/" + clinicId + "/doctors/" + doctorId + "/schedule")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"windows":[{"dayOfWeek":0,"startTime":"09:00","endTime":"12:00","slotMinutes":30}]}
                                """))
                .andExpect(status().isOk());
    }

    private String bookBody(String start, String end) {
        return String.format("""
                {"clinicId":"%s","doctorId":"%s","start":"%s","end":"%s","reason":"checkup"}
                """, clinicId, doctorId, start, end);
    }

    private org.springframework.test.web.servlet.ResultActions book(
            Cookie who, String body, String idemKey) throws Exception {
        var req = post("/api/v1/appointments")
                .cookie(who)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (idemKey != null) {
            req = req.header("Idempotency-Key", idemKey);
        }
        return mvc.perform(req);
    }

    // ---------- booking ----------

    @Test
    void book_happyPath_slotRemovedFromAvailability() throws Exception {
        String body = bookBody(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z");
        book(patient, body, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.start").value(MONDAY + "T09:00:00Z"))
                .andExpect(jsonPath("$.end").value(MONDAY + "T09:30:00Z"))
                .andExpect(jsonPath("$.clinicId").value(clinicId))
                .andExpect(jsonPath("$.doctorId").value(doctorId));

        mvc.perform(get(availability)
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].start").value(MONDAY + "T09:30:00Z"));
    }

    @Test
    void book_offGridAndOutsideSchedule_rejected() throws Exception {
        // 09:15 is not on the 30-minute grid
        book(patient, bookBody(MONDAY + "T09:15:00Z", MONDAY + "T09:45:00Z"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        // 13:00 is outside the 09:00–12:00 window entirely
        book(patient, bookBody(MONDAY + "T13:00:00Z", MONDAY + "T13:30:00Z"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        // end before start is a request-validation failure, not a slot conflict
        book(patient, bookBody(MONDAY + "T10:00:00Z", MONDAY + "T09:30:00Z"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.end").exists());
    }

    @Test
    void book_beyondLookaheadWindow_rejected() throws Exception {
        jdbc.update("UPDATE clinics SET slot_lookahead_days = 7 WHERE id = ?::uuid", clinicId);
        // Monday three weeks out is a valid schedule day but outside the window
        book(patient, bookBody(MONDAY.plusWeeks(2) + "T09:00:00Z",
                MONDAY.plusWeeks(2) + "T09:30:00Z"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));
    }

    @Test
    void book_pastSlot_rejected() throws Exception {
        book(patient, bookBody("2020-01-06T09:00:00Z", "2020-01-06T09:30:00Z"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.from").exists());
    }

    @Test
    void book_concurrentSameSlot_exactlyOneWins() throws Exception {
        String body = bookBody(MONDAY + "T10:00:00Z", MONDAY + "T10:30:00Z");
        int racers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                Cookie who = i == 0 ? patient : verifiedSession("rc-" + sfx() + "@test.local");
                tasks.add(() -> {
                    gate.await();
                    return book(who, body, null).andReturn()
                            .getResponse().getStatus();
                });
            }
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> t : tasks) {
                futures.add(pool.submit(t));
            }
            gate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            long wins = statuses.stream().filter(s -> s == 201).count();
            long losses = statuses.stream().filter(s -> s == 409).count();
            assertThat(wins).as("exactly one booking succeeds").isEqualTo(1);
            assertThat(losses).as("losers get 409 SLOT_TAKEN").isEqualTo(racers - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- idempotency ----------

    @Test
    void book_sameKeySamePayload_replaysOriginal() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = bookBody(MONDAY + "T11:00:00Z", MONDAY + "T11:30:00Z");

        MvcResult first = book(patient, body, key)
                .andExpect(status().isCreated()).andReturn();
        String id = jsonField(first.getResponse().getContentAsString(), "id");

        book(patient, body, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        // still only one appointment
        mvc.perform(get("/api/v1/appointments/mine").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void book_sameKeyDifferentPayload_conflict() throws Exception {
        String key = UUID.randomUUID().toString();
        book(patient, bookBody(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z"), key)
                .andExpect(status().isCreated());
        book(patient, bookBody(MONDAY + "T10:00:00Z", MONDAY + "T10:30:00Z"), key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void book_malformedIdempotencyKey_400() throws Exception {
        book(patient, bookBody(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z"), "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['Idempotency-Key']").exists());
    }

    // ---------- listing & detail ----------

    @Test
    void mine_upcomingAndPast() throws Exception {
        book(patient, bookBody(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z"), null)
                .andExpect(status().isCreated());
        UUID patientId = users.findByEmail(patientEmail).orElseThrow().getId();
        jdbc.update("""
                INSERT INTO appointments
                  (id, clinic_id, doctor_id, patient_id, slot, status, created_by)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?,
                        tstzrange(?, ?, '[)'), 'COMPLETED', ?)
                """,
                clinicId, doctorId, patientId,
                java.sql.Timestamp.from(Instant.now().minusSeconds(86400 * 3)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(86400 * 3 - 1800)),
                patientId);

        mvc.perform(get("/api/v1/appointments/mine").cookie(patient)
                        .param("when", "upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("BOOKED"));

        mvc.perform(get("/api/v1/appointments/mine").cookie(patient)
                        .param("when", "past"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        mvc.perform(get("/api/v1/appointments/mine").cookie(patient)
                        .param("when", "bogus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_ownOnly_foreignIs404() throws Exception {
        MvcResult r = book(patient, bookBody(MONDAY + "T09:00:00Z",
                MONDAY + "T09:30:00Z"), null)
                .andExpect(status().isCreated()).andReturn();
        String id = jsonField(r.getResponse().getContentAsString(), "id");

        mvc.perform(get("/api/v1/appointments/" + id).cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        Cookie stranger = verifiedSession("st-" + sfx() + "@test.local");
        mvc.perform(get("/api/v1/appointments/" + id).cookie(stranger))
                .andExpect(status().isNotFound());
        // even the clinic's own admin cannot read a patient's appointment
        mvc.perform(get("/api/v1/appointments/" + id).cookie(admin))
                .andExpect(status().isNotFound());
    }

    // ---------- cancellation ----------

    @Test
    void cancel_freesSlot_andIsIdempotent() throws Exception {
        MvcResult r = book(patient, bookBody(MONDAY + "T09:00:00Z",
                MONDAY + "T09:30:00Z"), null)
                .andExpect(status().isCreated()).andReturn();
        String id = jsonField(r.getResponse().getContentAsString(), "id");

        mvc.perform(post("/api/v1/appointments/" + id + "/cancel")
                        .cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists());

        // slot is bookable again
        mvc.perform(get(availability)
                        .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        // second cancel returns current state, not an error
        mvc.perform(post("/api/v1/appointments/" + id + "/cancel")
                        .cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_insideCutoff_rejected() throws Exception {
        UUID patientId = users.findByEmail(patientEmail).orElseThrow().getId();
        String id = jdbc.queryForObject("""
                INSERT INTO appointments
                  (id, clinic_id, doctor_id, patient_id, slot, created_by)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?,
                        tstzrange(?, ?, '[)'), ?)
                RETURNING id::text
                """,
                String.class, clinicId, doctorId, patientId,
                java.sql.Timestamp.from(Instant.now().plusSeconds(12 * 3600)),
                java.sql.Timestamp.from(Instant.now().plusSeconds(12 * 3600 + 1800)),
                patientId);

        // cutoff is 24h — a booking 12h out is uncancellable by the patient
        mvc.perform(post("/api/v1/appointments/" + id + "/cancel")
                        .cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_WINDOW_PASSED"))
                .andExpect(jsonPath("$.fields.cancellationCutoffHours").value("24"));
    }

    @Test
    void cancel_completedAppointment_illegalTransition() throws Exception {
        UUID patientId = users.findByEmail(patientEmail).orElseThrow().getId();
        String id = jdbc.queryForObject("""
                INSERT INTO appointments
                  (id, clinic_id, doctor_id, patient_id, slot, status, created_by)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?,
                        tstzrange(?, ?, '[)'), 'COMPLETED', ?)
                RETURNING id::text
                """,
                String.class, clinicId, doctorId, patientId,
                java.sql.Timestamp.from(Instant.now().minusSeconds(86400)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(86400 - 1800)),
                patientId);

        mvc.perform(post("/api/v1/appointments/" + id + "/cancel")
                        .cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS_TRANSITION"));
    }

    @Test
    void cancel_foreignAppointment_404() throws Exception {
        MvcResult r = book(patient, bookBody(MONDAY + "T09:00:00Z",
                MONDAY + "T09:30:00Z"), null)
                .andExpect(status().isCreated()).andReturn();
        String id = jsonField(r.getResponse().getContentAsString(), "id");

        Cookie stranger = verifiedSession("cn-" + sfx() + "@test.local");
        mvc.perform(post("/api/v1/appointments/" + id + "/cancel")
                        .cookie(stranger)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNotFound());
        // still booked — the stranger's attempt changed nothing
        mvc.perform(get("/api/v1/appointments/" + id).cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void book_anonymous_401() throws Exception {
        mvc.perform(post("/api/v1/appointments")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookBody(MONDAY + "T09:00:00Z", MONDAY + "T09:30:00Z")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers (shared shape with AvailabilityIT) ----------

    private String sfx() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Cookie verifiedSession(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"email":"%s","password":"%s","confirmPassword":"%s","fullName":"T"}
                                """, email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/v1/auth/verify-email")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"code\":\"%s\"}",
                                email, lastCodeSentTo(email))))
                .andExpect(status().isOk())
                .andReturn();
        return new Cookie("cp_access", cookieValue(r, "cp_access"));
    }

    private String createClinicAs(Cookie access) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics")
                        .cookie(access)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"Clinic %s\",\"timezone\":\"UTC\"}", sfx())))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "id");
    }

    private String invite(String clinicId, Cookie admin, String email, String role) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics/" + clinicId + "/staff")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"email":"%s","fullName":"Dr T","role":"%s"}
                                """, email, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonField(r.getResponse().getContentAsString(), "membershipId");
    }

    private String createProfile(String clinicId, Cookie admin, String membershipId)
            throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clinics/" + clinicId + "/doctors")
                        .cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"membershipId":"%s","specialtyId":"%s","consultMinutes":30}
                                """, membershipId, specialty)))
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

    private String cookieValue(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .map(h -> h.substring(h.indexOf('=') + 1, h.indexOf(';')))
                .orElse(null);
    }
}
