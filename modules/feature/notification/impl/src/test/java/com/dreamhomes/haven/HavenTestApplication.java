package com.dreamhomes.haven;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Test-only Spring Boot application root for feature-notification-impl. Same rationale
 * as the legacy-features test app — production {@code DreamhomesHavenApplication} lives
 * in the {@code app} module, which is downstream and not on this module's test
 * classpath.
 */
@SpringBootApplication
@EnableScheduling
public class HavenTestApplication {
}
