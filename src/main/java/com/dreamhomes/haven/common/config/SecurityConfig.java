package com.dreamhomes.haven.common.config;

import com.dreamhomes.haven.auth.JwtAuthenticationFilter;
import com.dreamhomes.haven.dreamai.config.DreamAiRateLimitProperties;
import com.dreamhomes.haven.common.web.ProblemDetailAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Deny-by-default security baseline.
 *
 * <p>Every request requires authentication unless a feature explicitly opens an endpoint
 * (e.g. public listing browse, auth/register, auth/login). Disabling CSRF and using a
 * stateless session policy reflect a JWT-bearing API — no server-side session state.
 *
 * <p>This baseline forces every new controller to declare its security posture by
 * either adding a permitAll() rule or being authenticated by default.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(DreamAiRateLimitProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.dreamhomes.haven.dreamai.ratelimit.DreamAiRateLimitFilter dreamAiRateLimitFilter;
    private final List<String> allowedOrigins;
    private final String errorTypeBase;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          com.dreamhomes.haven.dreamai.ratelimit.DreamAiRateLimitFilter dreamAiRateLimitFilter,
                          @Value("${cors.allowed-origins}") List<String> allowedOrigins,
                          @Value("${haven.errors.type-base:https://github.com/DreamHom/haven/blob/main/docs/errors/}")
                          String errorTypeBase) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.dreamAiRateLimitFilter = dreamAiRateLimitFilter;
        this.allowedOrigins = allowedOrigins;
        this.errorTypeBase = errorTypeBase;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new ProblemDetailAuthenticationEntryPoint(objectMapper, errorTypeBase);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Cookie"));
        cfg.setExposedHeaders(List.of("Location", "Retry-After"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemDetailAuthenticationEntryPoint problemEntryPoint) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot's `/error` dispatcher renders the body for any servlet
                        // forward (validation 400s, type-mismatch 400s, 404s on unmapped paths).
                        // It MUST be permitAll, otherwise the auth filter rewrites every error
                        // response to 401 — turning a "reason field empty" 400 into a misleading
                        // "unauthenticated" 401. Persona audit (Dayo) caught this on the
                        // RejectWithEmptyReason flow.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        // Liveness/readiness probes for load balancers + k8s. /actuator/prometheus
                        // is deliberately NOT in this list — scraping stays auth-gated.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        // OpenAPI spec + Swagger UI + Scalar renderer — public read so the
                        // frontend can discover the surface area without a token.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/scalar.html").permitAll()
                        // Favicon family — browsers and Railway's edge probe these without a
                        // token, and a 401 here turns into noisy 502/401 cycles on every page
                        // load (Vista deployment surfaced this in production). Bytes served
                        // from `src/main/resources/static/`, sourced from vista's app icon.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/favicon.ico", "/favicon.png", "/apple-touch-icon.png",
                                "/apple-touch-icon-precomposed.png").permitAll()
                        // Public read endpoints — both GET and HEAD (HEAD probes for cache-friendliness
                        // shouldn't require auth where GET doesn't; B-4 from persona audit).
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/listings", "/api/listings/*", "/api/listings/*/slots",
                                "/api/listings/*/comments",
                                "/api/listings/*/reviews",
                                "/api/listings/*/photos",
                                "/api/listings/*/videos",
                                "/api/users/*/profile",
                                "/api/users/*/reviews",
                                "/api/agents").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.HEAD,
                                "/api/listings", "/api/listings/*", "/api/listings/*/slots",
                                "/api/listings/*/comments",
                                "/api/listings/*/reviews",
                                "/api/listings/*/photos",
                                "/api/listings/*/videos",
                                "/api/users/*/profile",
                                "/api/users/*/reviews",
                                "/api/agents").permitAll()
                        // Dream AI search-by-prompt is public (consistent with /api/listings being
                        // public). Vista's /dream-ai page calls these SSR-side without a JWT.
                        // /api/dream-ai/chats* (history) stays auth-gated — that's per-user data.
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/dream-ai/suggestions",
                                "/api/dream-ai/turns/stream").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(problemEntryPoint))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(dreamAiRateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }
}
