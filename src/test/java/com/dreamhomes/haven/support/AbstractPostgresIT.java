package com.dreamhomes.haven.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests that need a real Postgres database (and now Kafka).
 *
 * <ul>
 *   <li>Postgres container starts once per JVM (static initializer) and is shared across
 *       every subclass; Spring's {@code @ServiceConnection} wires the datasource for free.</li>
 *   <li>Embedded Kafka starts once per JVM, exposing its bootstrap servers via the
 *       {@code spring.kafka.bootstrap-servers} property — so any production
 *       {@code @KafkaListener} or {@code KafkaTemplate} bean works against this in tests.</li>
 *   <li>Spring's package-walk finds {@code DreamhomesHavenApplication} (the only
 *       {@code @SpringBootConfiguration} on the classpath) automatically — no explicit
 *       {@code classes=} needed.</li>
 * </ul>
 *
 * <p>If a future test genuinely doesn't want Kafka along for the ride, exclude the autoconfig
 * locally with {@code @TestPropertySource}. Default for ITs is "everything wired."
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestExecutionListeners(
        // afterTestMethod runs in REVERSE order of this list. Putting our cleanup
        // FIRST means it runs LAST — after Transactional has rolled back, the
        // connection is fresh again and our TRUNCATE can run cleanly.
        listeners = {
                DatabaseCleanupTestExecutionListener.class,
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class,
                TransactionalTestExecutionListener.class
        }
)
public abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Production has no defaults for {@code haven.jwt.private-key} / {@code public-key}, so
     * ITs must wire a real keypair. The pair lives at {@code test/resources/jwt/} —
     * see {@link JwtTestKeys}.
     */
    @DynamicPropertySource
    static void registerJwtAndCorsProperties(DynamicPropertyRegistry registry) {
        registry.add("haven.jwt.private-key", () -> JwtTestKeys.PRIVATE_KEY_PEM);
        registry.add("haven.jwt.public-key", () -> JwtTestKeys.PUBLIC_KEY_PEM);
        registry.add("haven.jwt.issuer", () -> "dreamhomes-haven-test");
        registry.add("haven.jwt.audience", () -> "dreamhomes-test");
        registry.add("haven.jwt.expiration-ms", () -> "3600000");
        registry.add("cors.allowed-origins", () -> "http://localhost:3000");
        // Production has no defaults for the seeded-admin Flyway placeholders, so ITs
        // must set them explicitly. Hash below = bcrypt-10 of "test-admin-password".
        registry.add("ADMIN_EMAIL", () -> "admin@dreamhomes.test");
        registry.add("ADMIN_PASSWORD_HASH",
                () -> "$2a$10$0DWKxqZlDpa8XPM9zh4oVeobo1/wGsLxey1nnTC/BBuC.n/ilb9F.");
        // Force local photo storage in ITs even if .env has HAVEN_PHOTOS_STORAGE=r2.
        // ITs assert the synthesised media.dreamhomes.com URL shape — they don't and
        // shouldn't hit the real R2 bucket.
        registry.add("haven.photos.storage", () -> "local");
    }
}
