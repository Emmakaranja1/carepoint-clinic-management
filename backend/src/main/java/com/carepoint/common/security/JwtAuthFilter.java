package com.carepoint.common.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.carepoint.identity.persistence.ClinicMembershipRepository;

/**
 * Reads the access-token cookie, verifies the JWT, and installs a
 * {@link CarepointPrincipal} carrying the user's active clinic memberships
 * (loaded fresh — roles can't go stale inside a token). A bad or missing
 * token simply leaves the request anonymous; entry points decide 401 vs 403.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AuthCookies cookies;
    private final ClinicMembershipRepository memberships;

    public JwtAuthFilter(JwtService jwt, AuthCookies cookies, ClinicMembershipRepository memberships) {
        this.jwt = jwt;
        this.cookies = cookies;
        this.memberships = memberships;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        cookies.accessToken(request).ifPresent(token -> authenticate(token, request));
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Jwt jwt = this.jwt.verify(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            Map<UUID, String> roles = memberships
                    .findByUserIdAndStatus(userId, "ACTIVE")
                    .stream()
                    .collect(Collectors.toMap(
                            com.carepoint.identity.domain.ClinicMembership::getClinicId,
                            com.carepoint.identity.domain.ClinicMembership::getRole));

            CarepointPrincipal principal = new CarepointPrincipal(
                    userId, jwt.getClaimAsString("email"), "ACTIVE", roles);

            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception ignored) {
            // invalid/expired/forged token → request stays anonymous
        }
    }
}
