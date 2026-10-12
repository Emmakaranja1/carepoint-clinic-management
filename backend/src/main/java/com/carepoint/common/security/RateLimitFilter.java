package com.carepoint.common.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Fixed-window per-IP limiter for credential endpoints. In-memory and
 * per-instance — documented limitation; a shared limiter is a scaling
 * decision, not an MVP one.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private final Map<String, Rule> rules;
    private final java.util.List<Rule> wildcardRules;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public record Rule(String method, String path, int limitPerMinute) {
    }

    private record Window(long windowStart, AtomicInteger count) {
    }

    public RateLimitFilter(java.util.List<Rule> rules) {
        this.rules = new ConcurrentHashMap<>();
        this.wildcardRules = new java.util.ArrayList<>();
        for (Rule r : rules) {
            if (r.path().contains("*")) {
                this.wildcardRules.add(r);
            } else {
                this.rules.put(r.method() + " " + r.path(), r);
            }
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        Rule rule = rules.get(request.getMethod() + " " + uri);
        if (rule == null) {
            rule = matchWildcard(request.getMethod(), uri);
        }
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        long windowStart = now - (now % WINDOW_MS);
        String key = rule.path() + "|" + clientIp(request) + "|" + windowStart;

        windows.keySet().removeIf(k -> !k.endsWith("|" + windowStart));
        Window w = windows.computeIfAbsent(key, k -> new Window(windowStart, new AtomicInteger()));

        if (w.count().incrementAndGet() > rule.limitPerMinute()) {
            response.setStatus(ErrorCode.RATE_LIMITED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(mapper.writeValueAsString(
                    ApiError.of(ErrorCode.RATE_LIMITED, "Too many attempts — try again shortly")));
            return;
        }
        chain.doFilter(request, response);
    }

    /** '*' in a rule path matches exactly one URI path segment. */
    private Rule matchWildcard(String method, String uri) {
        String[] parts = uri.split("/");
        for (Rule r : wildcardRules) {
            if (!r.method().equals(method)) {
                continue;
            }
            String[] segs = r.path().split("/");
            if (segs.length != parts.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < segs.length; i++) {
                if (!segs[i].equals("*") && !segs[i].equals(parts[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return r;
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
