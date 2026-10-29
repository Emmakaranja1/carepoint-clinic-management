package com.carepoint.common.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.carepoint.common.config.AuthProperties;

/**
 * First-party session cookies per the auth strategy: HttpOnly, SameSite=Lax,
 * path-locked. Secure flag is env-driven (false only for local http dev).
 */
@Component
public class AuthCookies {

    private final AuthProperties props;

    public AuthCookies(AuthProperties props) {
        this.props = props;
    }

    public String accessCookie(String token) {
        return build(props.accessCookie(), token, props.accessTokenTtl());
    }

    public String refreshCookie(String token) {
        return build(props.refreshCookie(), token, props.sessionIdleTimeout());
    }

    public String clearAccessCookie() {
        return build(props.accessCookie(), "", Duration.ZERO);
    }

    public String clearRefreshCookie() {
        return build(props.refreshCookie(), "", Duration.ZERO);
    }

    public Optional<String> accessToken(HttpServletRequest request) {
        return cookieValue(request, props.accessCookie());
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return cookieValue(request, props.refreshCookie());
    }

    private String build(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString();
    }

    private Optional<String> cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
