package com.dreamhomes.haven.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real Postgres database.
 *
 * <p>The container starts once per JVM (static initializer) and is shared across all
 * subclasses for fast test runs. Spring's {@code @ServiceConnection} wires the container
 * directly to the application datasource, so no manual property plumbing is needed.
 *
 * <p>Kafka autoconfig is excluded here so integration tests don't try to reach a broker.
 * When a feature genuinely needs Kafka in tests, override the exclusion or layer in
 * spring-kafka-test's embedded broker.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
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
