package com.carepoint.identity.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.AuthCookies;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.identity.api.dto.ForgotPasswordRequest;
import com.carepoint.identity.api.dto.GoogleLoginRequest;
import com.carepoint.identity.api.dto.LoginRequest;
import com.carepoint.identity.api.dto.RegisterRequest;
import com.carepoint.identity.api.dto.RegisterResponse;
import com.carepoint.identity.api.dto.ResendCodeRequest;
import com.carepoint.identity.api.dto.ResetPasswordRequest;
import com.carepoint.identity.api.dto.UserResponse;
import com.carepoint.identity.application.AuthService;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.UserRepository;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthCookies cookies;
    private final UserRepository users;

    public AuthController(AuthService auth, AuthCookies cookies, UserRepository users) {
        this.auth = auth;
        this.cookies = cookies;
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = auth.register(
                req.email(), req.password(), req.confirmPassword(), req.fullName(), req.phone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getId(), user.getEmail(),
                        "Registration successful — you can now sign in"));
    }

    @PostMapping("/resend-code")
    public ResponseEntity<Void> resendCode(@Valid @RequestBody ResendCodeRequest req) {
        String purpose = req.purpose() == null ? "PASSWORD_RESET" : req.purpose();
        auth.resendCode(req.email(), purpose);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        auth.forgotPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.email(), req.code(), req.password(), req.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/google")
    public ResponseEntity<UserResponse> google(@Valid @RequestBody GoogleLoginRequest req) {
        return session(auth.googleLogin(req.idToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest req) {
        return session(auth.login(req.email(), req.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request) {
        String presented = cookies.refreshToken(request)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "No refresh token"));
        AuthService.AuthResult result = auth.refresh(presented);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.accessCookie(result.accessToken()))
                .header(HttpHeaders.SET_COOKIE, cookies.refreshCookie(result.refreshToken()))
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookies.refreshToken(request).ifPresent(auth::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearAccessCookie())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefreshCookie())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CarepointPrincipal principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required"));
        return toResponse(user);
    }

    private ResponseEntity<UserResponse> session(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.accessCookie(result.accessToken()))
                .header(HttpHeaders.SET_COOKIE, cookies.refreshCookie(result.refreshToken()))
                .body(toResponse(result.user()));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), auth.activeRoles(user.getId()));
    }
}
