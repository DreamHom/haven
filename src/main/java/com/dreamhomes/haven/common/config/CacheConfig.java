package com.dreamhomes.haven.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Server-side caching via Caffeine (in-process, no external infra). Picked over Redis
 * because at our scale a 30 ms cache miss to Postgres is fine and a second hop to a
 * Redis cluster would cost more than it saves — see {@code docs/TRADEOFFS.md}.
 *
 * <p>TTL matches the {@code Cache-Control: max-age=60} the public-discovery
 * interceptor stamps, so the two cache layers (CDN/browser + in-process) agree on
 * staleness. A listing-detail write evicts the cached entry immediately via
 * {@code @CacheEvict} so authenticated owners see their own edits without the TTL
 * delay anonymous readers tolerate.
 *
 * <p>Cache names are namespaced ({@code feature:purpose}) so a future operator can
 * inspect / instrument each one independently.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String LISTINGS_DETAIL = "listings:detail";
    public static final String LISTINGS_BROWSE = "listings:browse";
    public static final String USERS_PUBLIC_PROFILE = "users:publicProfile";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                LISTINGS_DETAIL, LISTINGS_BROWSE, USERS_PUBLIC_PROFILE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats());
        return manager;
    }
}
