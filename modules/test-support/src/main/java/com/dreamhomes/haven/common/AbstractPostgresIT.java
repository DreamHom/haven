package com.dreamhomes.haven.common;

import com.dreamhomes.haven.HavenTestApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
 *   <li>{@code classes = HavenTestApplication.class} pins the Spring Boot configuration
 *       explicitly. Without this, modules with {@code DreamhomesHavenApplication} on
 *       their classpath (just {@code app}) would see two {@code @SpringBootConfiguration}
 *       candidates; explicit {@code classes=} resolves the ambiguity.</li>
 * </ul>
 *
 * <p>If a future test genuinely doesn't want Kafka along for the ride, exclude the autoconfig
 * locally with {@code @TestPropertySource}. Default for ITs is "everything wired."
 */
@SpringBootTest(classes = HavenTestApplication.class)
@EmbeddedKafka(
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
public abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Production removes the placeholder default for {@code jwt.secret}, so ITs must
     * provide one explicitly. This value satisfies both the 32-byte minimum and the
     * placeholder check in {@code JwtService}.
     */
    @DynamicPropertySource
    static void registerJwtAndCorsProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "test-secret-for-haven-integration-tests-32+bytes");
        registry.add("jwt.issuer", () -> "dreamhomes-haven-test");
        registry.add("jwt.audience", () -> "dreamhomes-test");
        registry.add("cors.allowed-origins", () -> "http://localhost:3000");
    }
}
