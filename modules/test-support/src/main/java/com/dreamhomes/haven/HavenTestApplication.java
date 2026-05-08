package com.dreamhomes.haven;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Test-only Spring Boot application root for the {@code legacy-features} module.
 * <p>
 * The production {@link DreamhomesHavenApplication} lives in the {@code app} module,
 * which is a downstream consumer of this module — it's not on this module's test
 * classpath. Without an {@code @SpringBootConfiguration} reachable via package-walk,
 * every {@code @SpringBootTest} here would fail with "Unable to find a
 * @SpringBootConfiguration".
 * <p>
 * This class is intentionally test-scoped (under {@code src/test/java}) so it never
 * leaks into the production jar. {@code @EnableScheduling} mirrors the prod config
 * so {@link com.dreamhomes.haven.common.outbox.OutboxRelay}'s polling loop is wired
 * during integration tests, matching how the app actually runs.
 */
@SpringBootApplication
@EnableScheduling
public class HavenTestApplication {
}
