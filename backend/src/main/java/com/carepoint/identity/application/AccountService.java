package com.carepoint.identity.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.JwtService;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.RefreshTokenRepository;
import com.carepoint.identity.persistence.UserRepository;

@Service
public class AccountService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokenRepo;
    private final RefreshTokenService refreshTokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AccountService(UserRepository users,
                          RefreshTokenRepository refreshTokenRepo,
                          RefreshTokenService refreshTokens,
                          PasswordPolicy passwordPolicy,
                          PasswordEncoder passwords,
                          JwtService jwt) {
        this.users = users;
        this.refreshTokenRepo = refreshTokenRepo;
        this.refreshTokens = refreshTokens;
        this.passwordPolicy = passwordPolicy;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional
    public User updateProfile(UUID userId, String fullName, String phone) {
        User user = activeUser(userId);
        user.updateProfile(fullName.trim(), phone == null || phone.isBlank() ? null : phone.trim());
        return user;
    }

    /** Verifies the current password, then rotates credentials: every other session is revoked. */
    @Transactional
    public AuthService.AuthResult changePassword(UUID userId, String currentPassword,
                                                 String newPassword, String confirmPassword) {
        User user = activeUser(userId);
        if (user.getPasswordHash() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("currentPassword", "This account signs in with Google — use Forgot password to set one"));
        }
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("currentPassword", "Current password is incorrect"));
        }
        var violations = passwordPolicy.violations(newPassword);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password does not meet requirements",
                    Map.of("newPassword", passwordPolicy.describe(violations)));
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("confirmPassword", "Passwords do not match"));
        }

        user.changePassword(passwords.encode(newPassword));
        refreshTokenRepo.revokeAllForUser(user.getId());
        String refresh = refreshTokens.issue(user.getId()).token();
        return new AuthService.AuthResult(user, jwt.issueAccessToken(user.getId(), user.getEmail()), refresh);
    }

    private User activeUser(UUID userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required"));
    }
}
