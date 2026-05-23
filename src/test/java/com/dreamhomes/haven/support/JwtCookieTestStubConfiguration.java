package com.dreamhomes.haven.support;

import com.dreamhomes.haven.auth.cookie.JwtCookieService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link com.dreamhomes.haven.common.config.SecurityConfig} pulls in {@code JwtAuthenticationFilter},
 * which requires a {@link JwtCookieService} bean. {@code @WebMvcTest} slices do not component-scan
 * the main app, so tests that {@code @Import(SecurityConfig.class)} must supply this bean explicitly.
 */
@TestConfiguration
public class JwtCookieTestStubConfiguration {

    @Bean
    JwtCookieService jwtCookieService() {
        return new JwtCookieService(false, "haven_access", true, "/", "", "Lax", 3_600_000L);
    }
}
