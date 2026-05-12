package com.dreamhomes.haven.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting on the unauthenticated auth endpoints. Caps registration and
 * login attempts per IP — wide enough for a fumbled password + a password-manager retry
 * + a password-reset round-trip, narrow enough to make automated credential-stuffing slow.
 *
 * <p>State is in-memory only; for multi-instance deployments swap the
 * {@code ConcurrentHashMap} for a shared store (Redis, Hazelcast, etc.).
 *
 * <p>Returns {@code 429 Too Many Requests} with a Problem+JSON body and a
 * {@code Retry-After} header when exhausted.
 *
 * <p>Tuning: the persona audit caught the prior 5/min ceiling tripping legitimate
 * 6-persona QA runs and password-manager retries. Default is 30/min — override via
 * {@code haven.rate-limit.auth.capacity} / {@code window-seconds} per environment.
 *
 * <p>{@code /api/me/password} is included so the password-change path gets the same
 * brute-force protection login does — leaked-token + change-password is a common
 * account-takeover shape and shouldn't have unbounded attempts per IP.
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/me/password"
    );

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${haven.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${haven.rate-limit.auth.capacity:30}")
    private int capacity;

    @Value("${haven.rate-limit.auth.window-seconds:60}")
    private long windowSeconds;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!enabled || !RATE_LIMITED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        Duration window = Duration.ofSeconds(windowSeconds);
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, window)
                        .build())
                .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for {} on {}", key, request.getRequestURI());
            long retryAfter = window.toSeconds();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            // Problem+JSON body so the client can show a real "try again in N seconds"
            // message — persona audit (Temi) flagged the silent 429.
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429," +
                            "\"detail\":\"rate limit exceeded — try again in " + retryAfter + " seconds\"," +
                            "\"instance\":\"" + request.getRequestURI() + "\"," +
                            "\"retryAfterSeconds\":" + retryAfter + "}");
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {

            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
