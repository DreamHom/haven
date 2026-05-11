package com.dreamhomes.haven.common.config;

import com.dreamhomes.haven.common.web.PublicCacheHeadersInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-cutting MVC config that needs to be wired into every Spring Boot context
 * (production app + every test boot). Lives in {@code core} so it travels with the
 * shared infra rather than being scoped to {@code app}: integration tests that boot
 * {@code HavenTestApplication} (in {@code test-support}) need the same Page DTO
 * shape and the same public-cache-control headers as production.
 *
 * <ul>
 *   <li>Stabilises {@code Page} JSON output via {@code VIA_DTO} so the wire shape
 *       doesn't drift across Spring upgrades.</li>
 *   <li>Adds {@code Cache-Control} headers on public discovery paths so CDN/browser
 *       caches can serve repeat hits without round-tripping to Postgres.</li>
 * </ul>
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new PublicCacheHeadersInterceptor())
                .addPathPatterns(
                        "/api/listings",
                        "/api/listings/*",
                        "/api/listings/*/slots",
                        "/api/listings/*/comments",
                        "/api/listings/*/reviews",
                        "/api/listings/*/photos",
                        "/api/users/*/profile",
                        "/api/users/*/reviews"
                );
    }
}
