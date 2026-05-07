package com.dreamhomes.haven.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Adds {@code Cache-Control} on public read paths so a CDN or browser cache can serve
 * repeat hits without coming back to Postgres. Aligns with the system-architecture
 * promise that public discovery is fast and cacheable (PRD §6).
 *
 * <p>{@code stale-while-revalidate} keeps perceived latency low — the cache returns
 * the stale entry immediately and refreshes in the background, so a slow refresh
 * never blocks the page.
 */
public class PublicCacheHeadersInterceptor implements HandlerInterceptor {

    private static final String CACHE_HEADER = "public, max-age=60, stale-while-revalidate=300";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        response.setHeader("Cache-Control", CACHE_HEADER);
        return true;
    }
}
