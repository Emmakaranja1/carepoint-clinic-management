package com.carepoint.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.mail.MailService;
import com.carepoint.identity.domain.OtpCode;
import com.carepoint.identity.persistence.OtpCodeRepository;

/**
 * Six-digit email codes with bounded send and attempt budgets:
 *   * code valid for `otp.ttl` (default 2 min)
 *   * `otp.max-attempts` wrong tries (default 4) → code dies
 *   * `otp.max-sends` (default 4) within `otp.resend-cooldown` (default 104 s)
 *     → further sends rejected until the window passes
 * Codes are stored SHA-256-hashed; the plain code only ever travels by mail.
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository codes;
    private final MailService mail;
    private final OtpProperties props;

    public OtpService(OtpCodeRepository codes, MailService mail, OtpProperties props) {
        this.codes = codes;
        this.mail = mail;
        this.props = props;
    }

    /**
     * Issues a fresh code and mails it. Resend rule: within a cooldown
     * window starting at the first send's created_at, at most `maxSends`
     * codes may be issued; the next send after that waits out the window.
     */
    @Transactional
    public void send(String email, UUID userId, String purpose) {
        send(email, userId, purpose, null);
    }

    /**
     * Same as {@link #send(String, UUID, String)} but prepends a lead-in line
     * to the mail body (e.g. a staff-invite intro before the code).
     */
    @Transactional
    public void send(String email, UUID userId, String purpose, String leadIn) {
        Instant now = Instant.now();
        int sendCount = codes.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .map(latest -> nextSendCount(latest, now))
                .orElse(1);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpCode otp = new OtpCode(email, userId, purpose, hash(code),
                now.plus(props.ttl()), sendCount);
        codes.save(otp);
        mail.send(email, subject(purpose), body(code, purpose, leadIn));
    }

    private int nextSendCount(OtpCode latest, Instant now) {
        Instant windowEnd = latest.getCreatedAt().plus(props.resendCooldown());
        if (now.isAfter(windowEnd)) {
            return 1; // window expired — budget resets
        }
        if (latest.getSendCount() >= props.maxSends()) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Too many codes requested — please wait before asking for another");
        }
        return latest.getSendCount() + 1;
    }

    /**
     * Verifies a presented code against the latest issued one and consumes
     * it. Uniform errors — no hint whether the email, code or purpose
     * exists.
     */
    @Transactional
    public UUID verify(String email, String purpose, String code) {
        OtpCode otp = codes.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code"));

        if (otp.isConsumed() || otp.isExpired(Instant.now())) {
            throw new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code");
        }
        if (otp.getAttempts() >= props.maxAttempts()) {
            throw new ApiException(ErrorCode.OTP_INVALID,
                    "Too many attempts — request a new code");
        }

        codes.recordAttempt(otp.getId()); // commits even though a bad code throws below
        otp.recordAttempt();              // keep the managed entity consistent for the flush
        if (!hash(code).equals(otp.getCodeHash())) {
            throw new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code");
        }

        otp.consume();
        return otp.getUserId();
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String subject(String purpose) {
        return "VERIFY_EMAIL".equals(purpose)
                ? "CarePoint — verify your email"
                : "CarePoint — password reset code";
    }

    private String body(String code, String purpose, String leadIn) {
        String action = "VERIFY_EMAIL".equals(purpose)
                ? "verify your email address"
                : "set or reset your password";
        return (leadIn == null ? "" : leadIn + "\n\n")
                + "Your CarePoint code to " + action + " is: " + code
                + "\n\nIt expires in " + props.ttl().toMinutes() + " minutes."
                + "\nIf you did not request this, ignore this email.";
    }
}
