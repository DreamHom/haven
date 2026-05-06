package com.dreamhomes.haven.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Flyway runs on context startup and the V1 baseline migration is applied.
 *
 * <p>If a future migration is broken, this IT will fail before any feature test does,
 * making the failure cause obvious.
 */
class FlywayMigrationsIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void v1UsersMigrationIsApplied() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void usersTableExistsWithExpectedColumns() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name = 'users' AND column_name IN " +
                        "('id', 'email', 'password_hash', 'role', 'full_name', 'phone', 'created_at')",
                Integer.class);
        assertThat(columnCount).isEqualTo(7);
    }
}
