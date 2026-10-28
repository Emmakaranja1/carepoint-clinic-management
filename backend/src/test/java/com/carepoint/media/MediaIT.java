package com.carepoint.media;

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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

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
import com.carepoint.common.storage.LocalDiskStorageService;
import com.carepoint.media.application.MediaJanitor;
import com.carepoint.media.domain.MediaObject;
import com.carepoint.media.persistence.MediaObjectRepository;

/**
 * Media upload pipeline (docs/01 US-M1, docs/06 §7b, ADR-0004): mint →
 * presigned PUT → confirm with magic-byte verification, plus authz and
 * orphan cleanup.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carepoint.rate-limit.login-per-minute=1000",
        "carepoint.rate-limit.register-per-minute=1000",
        "carepoint.rate-limit.otp-per-minute=1000",
        "carepoint.rate-limit.availability-per-minute=1000",
        "carepoint.rate-limit.booking-per-minute=1000",
        "carepoint.rate-limit.media-upload-per-minute=1000"})
class MediaIT {

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
    MediaObjectRepository media;

    @Autowired
    LocalDiskStorageService storage;

    @Autowired
    MediaJanitor janitor;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    MailService mail;

    Cookie admin;
    Cookie patient;
    String clinicId;
    String doctorId;
    String mintUrl;

    @BeforeEach
    void setup() throws Exception {
        UUID specialty = specialties.save(new Specialty("S-" + sfx())).getId();
        admin = verifiedSession("md-admin-" + sfx() + "@test.local");
        patient = verifiedSession("md-pt-" + sfx() + "@test.local");
        clinicId = createClinicAs(admin);
        String membership = invite(clinicId, admin,
                "md-dr-" + sfx() + "@test.local", "DOCTOR");
        doctorId = createProfile(clinicId, admin, membership, specialty);
        mintUrl = "/api/v1/clinics/" + clinicId + "/media/uploads";
    }

    // ---------- happy path ----------

    @Test
    void upload_mintPutConfirm_objectBecomesPublic() throws Exception {
        MvcResult mint = mint(admin, "DOCTOR_PHOTO", "DOCTOR_PROFILE", doctorId,
                "image/png", 2048);
        String id = jsonField(mint, "id");
        String uploadUrl = jsonField(mint, "uploadUrl");

        byte[] png = imageBytes("png");
        // capability URL needs no cookie and no CSRF header
        mvc.perform(put(uploadUrl).contentType(MediaType.IMAGE_PNG).content(png))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/media/" + id + "/confirm").cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publicUrl").isNotEmpty());

        String publicUrl = jsonField(mint, "publicUrl");
        mvc.perform(get(publicUrl.replace("http://localhost:8080", "")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getContentAsByteArray()).isEqualTo(png));

        // idempotent re-confirm
        mvc.perform(post("/api/v1/media/" + id + "/confirm").cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---------- mint authorization + validation ----------

    @Test
    void mint_authzMatrix() throws Exception {
        String body = """
                {"purpose":"DOCTOR_PHOTO","ownerType":"DOCTOR_PROFILE","ownerId":"%s",
                 "contentType":"image/png","byteSize":2048}""".formatted(doctorId);
        mvc.perform(post(mintUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(mintUrl).cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        Cookie other = verifiedSession("md-x-" + sfx() + "@test.local");
        createClinicAs(other);
        mvc.perform(post(mintUrl).cookie(other)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void mint_validation() throws Exception {
        // disallowed type
        mvc.perform(post(mintUrl).cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DEMO_IMAGE\",\"contentType\":\"image/gif\","
                                + "\"byteSize\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.contentType").exists());
        // over the 5 MB ceiling (also caught by Bean Validation)
        mvc.perform(post(mintUrl).cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DEMO_IMAGE\",\"contentType\":\"image/png\","
                                + "\"byteSize\":5242881}"))
                .andExpect(status().isBadRequest());
        // doctor photo pointing at another clinic's profile → 404
        Cookie other = verifiedSession("md-y-" + sfx() + "@test.local");
        String otherClinic = createClinicAs(other);
        String otherMembership = invite(otherClinic, other,
                "md-ydr-" + sfx() + "@test.local", "DOCTOR");
        String otherDoctor = createProfile(otherClinic, other, otherMembership,
                specialties.save(new Specialty("S-" + sfx())).getId());
        mvc.perform(post(mintUrl).cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"purpose\":\"DOCTOR_PHOTO\",\"ownerType\":\"DOCTOR_PROFILE\","
                                + "\"ownerId\":\"%s\",\"contentType\":\"image/png\","
                                + "\"byteSize\":100}").formatted(otherDoctor)))
                .andExpect(status().isNotFound());
        // clinic logo must point at the clinic itself
        mvc.perform(post(mintUrl).cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"purpose\":\"CLINIC_LOGO\",\"ownerType\":\"CLINIC\","
                                + "\"ownerId\":\"%s\",\"contentType\":\"image/png\","
                                + "\"byteSize\":100}").formatted(otherClinic)))
                .andExpect(status().isBadRequest());
    }

    // ---------- presigned PUT ----------

    @Test
    void put_tamperedOrMismatchedRequestsRejected() throws Exception {
        MvcResult mint = mint(admin, "DEMO_IMAGE", null, null, "image/png", 2048);
        String uploadUrl = jsonField(mint, "uploadUrl");

        // bad signature
        mvc.perform(put(uploadUrl.replaceAll("sig=[0-9a-f]+", "sig=deadbeef"))
                        .contentType(MediaType.IMAGE_PNG).content(imageBytes("png")))
                .andExpect(status().isForbidden());
        // expired capability
        mvc.perform(put(uploadUrl.replaceAll("exp=\\d+", "exp=1"))
                        .contentType(MediaType.IMAGE_PNG).content(imageBytes("png")))
                .andExpect(status().isForbidden());
        // Content-Type ≠ the signed content type
        mvc.perform(put(uploadUrl)
                        .contentType(MediaType.IMAGE_JPEG).content(imageBytes("jpg")))
                .andExpect(status().isBadRequest());
        // missing signature params
        mvc.perform(put(uploadUrl.substring(0, uploadUrl.indexOf('?')))
                        .contentType(MediaType.IMAGE_PNG).content(imageBytes("png")))
                .andExpect(status().isBadRequest());
        // body exceeding the signed ceiling
        byte[] tooBig = new byte[3000];
        mvc.perform(put(uploadUrl).contentType(MediaType.IMAGE_PNG).content(tooBig))
                .andExpect(status().isBadRequest());
    }

    // ---------- confirm verification ----------

    @Test
    void confirm_magicByteMismatch_rejectedAndUnreachable() throws Exception {
        MvcResult mint = mint(admin, "DEMO_IMAGE", null, null, "image/png", 4096);
        String id = jsonField(mint, "id");
        String uploadUrl = jsonField(mint, "uploadUrl");
        String publicUrl = jsonField(mint, "publicUrl");

        // declared PNG, actually JPEG — the Content-Type claim passes PUT
        mvc.perform(put(uploadUrl).contentType(MediaType.IMAGE_PNG)
                        .content(imageBytes("jpg")))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/media/" + id + "/confirm").cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.file").exists());

        // row stays PENDING for the janitor; object deleted → unreachable
        assertThat(media.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo("PENDING");
        mvc.perform(get(publicUrl.replace("http://localhost:8080", "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirm_objectMissingOrOversize_rejected() throws Exception {
        MvcResult mint = mint(admin, "DEMO_IMAGE", null, null, "image/png", 2048);
        String id = jsonField(mint, "id");

        // never uploaded
        mvc.perform(post("/api/v1/media/" + id + "/confirm").cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest());

        // object planted directly, over the ceiling (bypasses the PUT cap)
        MediaObject row = media.findById(UUID.fromString(id)).orElseThrow();
        Path p = storage.resolve(row.getObjectKey());
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[(int) (5 * 1024 * 1024 + 1)]);
        mvc.perform(post("/api/v1/media/" + id + "/confirm").cookie(admin)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirm_authz() throws Exception {
        MvcResult mint = mint(admin, "DEMO_IMAGE", null, null, "image/png", 2048);
        String id = jsonField(mint, "id");
        mvc.perform(put(jsonField(mint, "uploadUrl"))
                        .contentType(MediaType.IMAGE_PNG).content(imageBytes("png")))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/media/" + id + "/confirm")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/media/" + id + "/confirm").cookie(patient)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden());
        Cookie other = verifiedSession("md-z-" + sfx() + "@test.local");
        createClinicAs(other);
        mvc.perform(post("/api/v1/media/" + id + "/confirm").cookie(other)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNotFound());
    }

    // ---------- janitor ----------

    @Test
    void janitor_reapsStalePendingRows() throws Exception {
        MvcResult mint = mint(admin, "DEMO_IMAGE", null, null, "image/png", 2048);
        String id = jsonField(mint, "id");
        mvc.perform(put(jsonField(mint, "uploadUrl"))
                        .contentType(MediaType.IMAGE_PNG).content(imageBytes("png")))
                .andExpect(status().isNoContent());
        Path object = storage.resolve(
                media.findById(UUID.fromString(id)).orElseThrow().getObjectKey());
        assertThat(object).exists();

        // fresh pending rows are NOT reaped
        janitor.reapStaleUploads();
        assertThat(media.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo("PENDING");
        assertThat(object).exists();

        // backdate past the 24h window → ORPHANED + object deleted
        jdbc.update("UPDATE media_objects SET created_at = ? WHERE id = ?::uuid",
                java.sql.Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)), id);
        janitor.reapStaleUploads();
        assertThat(media.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo("ORPHANED");
        assertThat(object).doesNotExist();
    }

    // ---------- helpers ----------

    private MvcResult mint(Cookie as, String purpose, String ownerType,
                           String ownerId, String contentType, long byteSize)
            throws Exception {
        StringBuilder body = new StringBuilder("{\"purpose\":\"" + purpose + "\"");
        if (ownerType != null) {
            body.append(",\"ownerType\":\"").append(ownerType).append("\"");
        }
        if (ownerId != null) {
            body.append(",\"ownerId\":\"").append(ownerId).append("\"");
        }
        body.append(",\"contentType\":\"").append(contentType)
                .append("\",\"byteSize\":").append(byteSize).append('}');
        return mvc.perform(post(mintUrl).cookie(as)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private byte[] imageBytes(String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB),
                format, out);
        return out.toByteArray();
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

    private String invite(String clinicId, Cookie admin, String email, String role)
            throws Exception {
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

    private String createProfile(String clinicId, Cookie admin, String membershipId,
                                 UUID specialty) throws Exception {
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

    private String lastCodeSentTo(String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), body.capture());
        String last = body.getAllValues().get(body.getAllValues().size() - 1);
        Matcher m = CODE_IN_BODY.matcher(last);
        assertThat(m.find()).as("OTP present in mail body").isTrue();
        return m.group(1);
    }

    private String jsonField(MvcResult r, String field) throws Exception {
        return jsonField(r.getResponse().getContentAsString(), field);
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
