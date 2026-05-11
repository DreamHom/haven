package com.dreamhomes.haven.common;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0 foundation: proves the rails are in place before any feature lands.
 *
 * <ol>
 *   <li>Postgres container boots and JdbcTemplate can talk to it.</li>
 *   <li>SecurityConfig denies all requests by default — an unauth call to any path is 401.</li>
 * </ol>
 *
 * <p>If either of these breaks, every feature test downstream becomes unreliable, so this
 * IT is the canary.
 */
@AutoConfigureMockMvc
class Phase0FoundationIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Test
    void postgresContainerBootsAndIsReachable() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    void unauthenticatedRequestToAnyPathReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/any-undefined-path"))
                .andExpect(status().isUnauthorized());
    }
}
