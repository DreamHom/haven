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
 * login attempts at 5 per minute per IP — enough room for a fumbled password, narrow
 * enough to make automated credential-stuffing slow.
 *
 * <p>State is in-memory only; for multi-instance deployments swap the
 * {@code ConcurrentHashMap} for a shared store (Redis, Hazelcast, etc.).
 *
 * <p>Returns {@code 429 Too Many Requests} with no body when exhausted.
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    private static final int CAPACITY = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Toggle for slice tests that focus on controller behaviour and don't want to fight
     * the bucket. Production and full ITs leave this at the default {@code true}.
     */
    @Value("${haven.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!enabled || !RATE_LIMITED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(CAPACITY)
                        .refillIntervally(CAPACITY, WINDOW)
                        .build())
                .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for {} on {}", key, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated chain; the leftmost is the originator.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
