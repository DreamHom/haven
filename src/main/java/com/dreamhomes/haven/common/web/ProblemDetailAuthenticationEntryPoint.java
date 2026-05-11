package com.dreamhomes.haven.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URI;

/**
 * Emits a Problem+JSON body on 401 instead of leaving the response empty.
 * Persona audit (Ngozi, Temi) flagged silent 401s as misleading — a real user
 * gets nothing back and assumes the platform is broken. This restores parity
 * with the 4xx shapes coming out of {@link GlobalExceptionHandler}.
 *
 * <p>Not a {@code @Component} on purpose — wired as a {@code @Bean} inside
 * SecurityConfig so it's always loaded whenever SecurityConfig is, including
 * in {@code @WebMvcTest} slices.</p>
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final String errorTypeBase;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper, String errorTypeBase) {
        this.objectMapper = objectMapper;
        this.errorTypeBase = errorTypeBase;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        problem.setType(URI.create(errorTypeBase + "unauthenticated"));
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
