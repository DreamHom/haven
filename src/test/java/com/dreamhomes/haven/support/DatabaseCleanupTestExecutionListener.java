package com.dreamhomes.haven.support;

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
                listing_reviews,
                listing_saves,
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
                listings,
                properties,
                agent_profiles,
                agent_marketing_media,
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
    }
}
