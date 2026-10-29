package com.carepoint.identity.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.common.security.AuthCookies;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.identity.api.dto.ChangePasswordRequest;
import com.carepoint.identity.api.dto.UpdateProfileRequest;
import com.carepoint.identity.api.dto.UserResponse;
import com.carepoint.identity.application.AccountService;
import com.carepoint.identity.application.AuthService;

/** Self-service account management for the signed-in user. */
@RestController
@RequestMapping("/api/v1/auth")
public class AccountController {

    private final AccountService accounts;
    private final AuthService auth;
    private final AuthCookies cookies;

    public AccountController(AccountService accounts, AuthService auth, AuthCookies cookies) {
        this.accounts = accounts;
        this.auth = auth;
        this.cookies = cookies;
    }

    @PatchMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal CarepointPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        var user = accounts.updateProfile(principal.userId(), req.fullName(), req.phone());
        return UserResponse.of(user, auth.activeRoles(user.getId()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CarepointPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest req) {
        AuthService.AuthResult result = accounts.changePassword(
                principal.userId(), req.currentPassword(), req.newPassword(), req.confirmPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.accessCookie(result.accessToken()))
                .header(HttpHeaders.SET_COOKIE, cookies.refreshCookie(result.refreshToken()))
                .build();
    }
}
