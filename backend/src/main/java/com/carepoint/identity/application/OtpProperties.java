package com.carepoint.identity.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carepoint.otp")
public record OtpProperties(
        Duration ttl,               // how long a code is usable — 2 min
        int maxAttempts,            // wrong-code tries before the code dies — 4
        int maxSends,               // sends allowed inside the cooldown window — 4
        Duration resendCooldown) {  // wait required after maxSends — 104 s
}
