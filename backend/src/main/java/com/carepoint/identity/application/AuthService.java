package com.carepoint.identity.application;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.JwtService;
import com.carepoint.identity.domain.ClinicMembership;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.ClinicMembershipRepository;
import com.carepoint.identity.persistence.RefreshTokenRepository;
import com.carepoint.identity.persistence.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final ClinicMembershipRepository memberships;
    private final RefreshTokenRepository refreshTokenRepo;
    private final RefreshTokenService refreshTokens;
    private final OtpService otp;
    private final PasswordPolicy passwordPolicy;
    private final GoogleIdentityVerifier googleVerifier;
    private final JwtService jwt;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users,
                       ClinicMembershipRepository memberships,
                       RefreshTokenRepository refreshTokenRepo,
                       RefreshTokenService refreshTokens,
                       OtpService otp,
                       PasswordPolicy passwordPolicy,
                       GoogleIdentityVerifier googleVerifier,
                       JwtService jwt,
                       PasswordEncoder passwords) {
        this.users = users;
        this.memberships = memberships;
        this.refreshTokenRepo = refreshTokenRepo;
        this.refreshTokens = refreshTokens;
        this.otp = otp;
        this.passwordPolicy = passwordPolicy;
        this.googleVerifier = googleVerifier;
        this.jwt = jwt;
        this.passwords = passwords;
    }

    /**
     * Patient self-registration. No role field exists — staff roles can only
     * come from clinic-admin endpoints, so privilege escalation by
     * registration is structurally impossible. Account is created unverified
     * and a verification code is mailed; login is gated on verification.
     */
    @Transactional
    public User register(String email, String password, String confirmPassword,
                         String fullName, String phone) {
        var violations = passwordPolicy.violations(password);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Password does not meet requirements",
                    Map.of("password", passwordPolicy.describe(violations)));
        }
        if (!password.equals(confirmPassword)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    Map.of("confirmPassword", "Passwords do not match"));
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        try {
            User user = users.save(User.localUser(email, passwords.encode(password), fullName, phone));
            users.flush();
            otp.send(email, user.getId(), "VERIFY_EMAIL");
            return user;
        } catch (DataIntegrityViolationException e) {
            // unique index still guards the register race
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
    }

    public AuthResult login(String email, String password) {
        User user = users.findByEmail(email)
                .filter(User::isActive)
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwords.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        if (!user.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Email not verified — check your inbox for the code");
        }
        return authResultFor(user, null);
    }

    /** Completes registration: valid code → email verified → session issued. */
    @Transactional
    public AuthResult verifyEmail(String email, String code) {
        UUID userId = otp.verify(email, "VERIFY_EMAIL", code);
        User user = users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code"));
        user.markEmailVerified();
        return authResultFor(user, null);
    }

    public void resendCode(String email, String purpose) {
        users.findByEmail(email)
                .filter(User::isActive)
                .ifPresent(u -> otp.send(email, u.getId(), purpose));
        // unknown emails → silently no-op; the endpoint always answers 204
    }

    /** Always 204s — the response must not reveal whether the email exists. */
    public void forgotPassword(String email) {
        users.findByEmail(email)
                .filter(User::isActive)
                .filter(u -> "LOCAL".equals(u.getAuthProvider()))
                .ifPresent(u -> otp.send(email, u.getId(), "PASSWORD_RESET"));
    }

    @Transactional
    public void resetPassword(String email, String code, String password, String confirmPassword) {
        var violations = passwordPolicy.violations(password);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Password does not meet requirements",
                    Map.of("password", passwordPolicy.describe(violations)));
        }
        if (!password.equals(confirmPassword)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    Map.of("confirmPassword", "Passwords do not match"));
        }

        UUID userId = otp.verify(email, "PASSWORD_RESET", code);
        User user = users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "Invalid or expired code"));

        user.changePassword(passwords.encode(password));
        user.markEmailVerified(); // a valid emailed code proves inbox ownership
        refreshTokenRepo.revokeAllForUser(user.getId()); // every live session dies
    }

    /** Google sign-in: verified ID token → existing account or a new OAuth user. */
    @Transactional
    public AuthResult googleLogin(String idToken) {
        GoogleIdentityVerifier.GoogleIdentity identity = googleVerifier.verify(idToken);
        User user = users.findByEmail(identity.email())
                .orElseGet(() -> users.save(
                        User.oauthUser(identity.email(), identity.name(), "GOOGLE",
                                identity.emailVerified())));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        // a Google-verified email on an existing local account settles verification
        if (identity.emailVerified() && !user.isEmailVerified()) {
            user.markEmailVerified();
        }
        return authResultFor(user, null);
    }

    public void logout(String presentedRefreshToken) {
        refreshTokens.revokeSession(presentedRefreshToken);
    }

    public Map<UUID, String> activeRoles(UUID userId) {
        return memberships.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .collect(Collectors.toMap(ClinicMembership::getClinicId, ClinicMembership::getRole));
    }

    private AuthResult authResultFor(User user, String refreshToken) {
        String refresh = refreshToken != null
                ? refreshToken
                : refreshTokens.issue(user.getId()).token();
        return new AuthResult(user, jwt.issueAccessToken(user.getId(), user.getEmail()), refresh);
    }

    public AuthResult refresh(String presentedRefreshToken) {
        RefreshTokenService.Issued rotated = refreshTokens.rotate(presentedRefreshToken);
        User user = users.findById(rotated.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        return authResultFor(user, rotated.token());
    }

    public record AuthResult(User user, String accessToken, String refreshToken) {
    }
}
