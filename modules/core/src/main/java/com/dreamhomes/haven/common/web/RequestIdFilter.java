package com.dreamhomes.haven.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every request with a correlation ID. If the caller sent
 * {@code X-Request-ID}, we honour it (so vista can pre-tag); otherwise we mint a
 * UUID. The id lands in:
 * <ul>
 *   <li>SLF4J MDC under the {@code requestId} key — every log line that runs on
 *       this request thread carries it (logback pattern includes {@code %X{requestId}}).</li>
 *   <li>The response header {@code X-Request-ID} — clients can quote it back in bug
 *       reports for fast log triage.</li>
 * </ul>
 *
 * <p>Runs at {@code HIGHEST_PRECEDENCE} so the ID is set before security, controllers,
 * exception handlers, anything else.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, requestId);
            response.setHeader(HEADER, requestId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
