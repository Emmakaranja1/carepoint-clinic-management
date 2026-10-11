package com.carepoint.common.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.carepoint.common.error.ApiError;
import com.carepoint.common.error.ErrorCode;

/**
 * CSRF defense for the cookie session: browsers will not send a custom
 * header cross-origin without a CORS preflight, so requiring
 * X-Requested-With on every mutating request blocks classic CSRF.
 * (SameSite=Lax on the cookies is the second layer.)
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        if (MUTATING.contains(request.getMethod()) && request.getHeader("X-Requested-With") == null) {
            response.setStatus(ErrorCode.CSRF_HEADER_MISSING.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(mapper.writeValueAsString(
                    ApiError.of(ErrorCode.CSRF_HEADER_MISSING, "X-Requested-With header required")));
            return;
        }
        chain.doFilter(request, response);
    }
}
