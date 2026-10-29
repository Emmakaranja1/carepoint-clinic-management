package com.carepoint.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carepoint.auth")
public record AuthProperties(
        String jwtIssuer,
        String jwtSigningKey,
        Duration accessTokenTtl,
        Duration sessionIdleTimeout,
        String accessCookie,
        String refreshCookie,
        boolean cookieSecure) {
}
