package com.carepoint.common.config;

import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.carepoint.common.error.ApiError;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CsrfHeaderFilter;
import com.carepoint.common.security.JwtAuthFilter;
import com.carepoint.common.security.RateLimitFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, com.carepoint.identity.application.OtpProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            AuthProperties props,
            @Value("${carepoint.rate-limit.login-per-minute}") int loginLimit,
            @Value("${carepoint.rate-limit.register-per-minute}") int registerLimit,
            @Value("${carepoint.rate-limit.otp-per-minute}") int otpLimit) throws Exception {

        RateLimitFilter rateLimits = new RateLimitFilter(List.of(
                new RateLimitFilter.Rule("POST", "/api/v1/auth/login", loginLimit),
                new RateLimitFilter.Rule("POST", "/api/v1/auth/register", registerLimit),
                new RateLimitFilter.Rule("POST", "/api/v1/auth/forgot-password", otpLimit),
                new RateLimitFilter.Rule("POST", "/api/v1/auth/resend-code", otpLimit),
                new RateLimitFilter.Rule("POST", "/api/v1/auth/verify-email", otpLimit),
                new RateLimitFilter.Rule("POST", "/api/v1/auth/reset-password", otpLimit),
                new RateLimitFilter.Rule("POST", "/api/v1/auth/google", otpLimit)));

        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/ping",
                        "/actuator/health", "/actuator/health/**",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/resend-code",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/google")
                .permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeError(res, ErrorCode.UNAUTHENTICATED, "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> writeError(res, ErrorCode.ACCESS_DENIED, "Access denied")))
            .addFilterBefore(rateLimits, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new CsrfHeaderFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse res, ErrorCode code, String message)
            throws java.io.IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(new ObjectMapper().writeValueAsString(ApiError.of(code, message)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties props) {
        byte[] key = props.jwtSigningKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(new SecretKeySpec(key, "HmacSHA256")));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties props) {
        byte[] key = props.jwtSigningKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwtIssuer()));
        return decoder;
    }
}
