package com.dreamhomes.haven.dreamai.ratelimit;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.dreamai.config.DreamAiRateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-authenticated-user token bucket for Dream AI turn POSTs (suggestions + SSE stream body).
 * Returns RFC 7807 Problem+JSON on exhaustion — see OpenAPI {@code DreamAiRateLimited}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DreamAiRateLimitFilter extends OncePerRequestFilter {

    private static final String SUGGESTIONS = "/api/dream-ai/suggestions";
    private static final String STREAM = "/api/dream-ai/turns/stream";

    private final DreamAiRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${haven.errors.type-base:https://github.com/DreamHom/haven/blob/main/docs/errors/}")
    private String errorTypeBase;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !SUGGESTIONS.equals(uri) && !STREAM.equals(uri);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        String key = rateLimitKey(request);
        Duration window = Duration.ofSeconds(properties.getWindowSeconds());
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getCapacity())
                        .refillIntervally(properties.getCapacity(), window)
                        .build())
                .build());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = window.toSeconds();
        log.warn("Dream AI rate limit exceeded for {} on {}", key, request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/problem+json");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Dream AI rate limit exceeded — try again in " + retryAfter + " seconds");
        pd.setType(java.net.URI.create(errorTypeBase + "dream-ai-rate-limited"));
        pd.setProperty("retryAfterSeconds", retryAfter);
        objectMapper.writeValue(response.getWriter(), pd);
    }

    private static String rateLimitKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtPrincipal p) {
            return "dream-ai:user:" + p.userId();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "dream-ai:ip:" + forwarded.split(",")[0].trim();
        }
        return "dream-ai:ip:" + request.getRemoteAddr();
    }
}
