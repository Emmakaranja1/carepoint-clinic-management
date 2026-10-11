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
import com.carepoint.identity.persistence.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final ClinicMembershipRepository memberships;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwt;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users,
                       ClinicMembershipRepository memberships,
                       RefreshTokenService refreshTokens,
                       JwtService jwt,
                       PasswordEncoder passwords) {
        this.users = users;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.passwords = passwords;
    }

    /**
     * Patient self-registration. No role field exists — staff roles can only
     * come from clinic-admin endpoints (M7), so privilege escalation by
     * registration is structurally impossible.
     */
    @Transactional
    public User register(String email, String password, String fullName, String phone) {
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        try {
            return users.save(new User(email, passwords.encode(password), fullName, phone));
        } catch (DataIntegrityViolationException e) {
            // unique index still guards the register race
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
    }

    public AuthResult login(String email, String password) {
        User user = users.findByEmail(email)
                .filter(User::isActive)
                .filter(u -> passwords.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        return authResultFor(user, null);
    }

    public AuthResult refresh(String presentedRefreshToken) {
        RefreshTokenService.Issued rotated = refreshTokens.rotate(presentedRefreshToken);
        User user = users.findById(rotated.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        return authResultFor(user, rotated.token());
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

    public record AuthResult(User user, String accessToken, String refreshToken) {
    }
}
