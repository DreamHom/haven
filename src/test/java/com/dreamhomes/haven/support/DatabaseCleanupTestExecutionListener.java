package com.dreamhomes.haven.support;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Wipes every Haven-owned table after each test method on {@link AbstractPostgresIT}.
 * Replaces the {@code @BeforeEach @AfterEach clean()} block that used to live inside
 * every IT and re-implement FK-ordered {@code deleteAll()} calls by hand.
 *
 * <p>One {@code TRUNCATE … RESTART IDENTITY CASCADE} statement is cheaper and safer
 * than a chain of repository deleteAll calls:</p>
 * <ul>
 *   <li>FK ordering doesn't matter — {@code CASCADE} handles it.</li>
 *   <li>Sequence counters reset, so tests assert on stable IDs (1, 2, 3…) when they
 *       don't care which user ID came first.</li>
 *   <li>Adding a new table only requires updating this list, not 20+ ITs.</li>
 * </ul>
 *
 * <p>The seeded V11 admin row gets wiped along with everything else — tests that need
 * an admin already re-seed via {@code jwtTestSupport.persistUser(Role.ADMIN)}, so this
 * matches the pre-existing assumption.</p>
 *
 * <p><b>Caffeine cache eviction (Item 1 follow-up).</b> After truncating the schema, we
 * also clear every Spring-managed cache so cached read-through entries from the previous
 * test don't survive into the next one (and fake hits on rows that no longer exist).
 * Without this step the {@code listings:detail}, {@code listings:browse}, and
 * {@code users:publicProfile} caches added by {@code CacheConfig} broke test isolation
 * for ITs that re-seed listings/users across methods (ListingFlowEndToEndIT,
 * ListingMapsAndMediaIT, ListingTrustSignalsIT).</p>
 */
public class DatabaseCleanupTestExecutionListener extends AbstractTestExecutionListener {

    /**
     * Order is irrelevant under CASCADE, but listing every table here is the canonical
     * inventory — when a new Flyway migration adds a table, this list is the one
     * place to update.
     */
    private static final String TRUNCATE_SQL = """
            TRUNCATE TABLE
                listing_reports,
                promotion_clicks,
                promotion_impressions,
                promotions,
                listing_reviews,
                listing_saves,
                photo_upload_intent,
                listing_photos,
                listing_leads,
                inspection_requests,
                inspection_slots,
                offers,
                comments,
                notifications,
                outbox,
                admin_audit_log,
                verifications,
                agent_listings,
                listing_search_embeddings,
                listings,
                properties,
                agent_profiles,
                agent_marketing_media,
                dream_ai_chat_messages,
                dream_ai_chats,
                users
            RESTART IDENTITY CASCADE
            """;

    /**
     * High order so we run after Spring's own listeners (e.g. @DirtiesContext, dependency
     * injection cleanup) but before the test context is torn down.
     */
    @Override
    public int getOrder() {
        return 9000;
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        ApplicationContext ctx = testContext.getApplicationContext();
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        jdbc.execute(TRUNCATE_SQL);
        evictAllCaches(ctx);
    }

    /**
     * Clears every named cache registered with the active {@link CacheManager}. Quietly
     * skips when no CacheManager is present (e.g. a slice test that excludes
     * {@code CacheConfig}) so existing tests without caching aren't affected.
     */
    private static void evictAllCaches(ApplicationContext ctx) {
        CacheManager cacheManager;
        try {
            cacheManager = ctx.getBean(CacheManager.class);
        } catch (NoSuchBeanDefinitionException ignored) {
            return;
        }
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
