package com.dreamhomes.haven.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on Spring Data JPA's auditing hooks so entities marked with
 * {@code @EntityListeners(AuditingEntityListener.class)} get their
 * {@code @CreatedDate} / {@code @LastModifiedDate} fields stamped automatically
 * on insert and update.
 *
 * <p>Lives in its own config class (rather than on {@code DreamhomesHavenApplication})
 * so the dependency on {@code spring-data-jpa}'s auditing surface is one focused
 * import in one focused file. If we ever introduce auditor-aware fields
 * ({@code @CreatedBy} / {@code @LastModifiedBy}), the {@code AuditorAware<Long>}
 * bean lands here next to the enable annotation.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
