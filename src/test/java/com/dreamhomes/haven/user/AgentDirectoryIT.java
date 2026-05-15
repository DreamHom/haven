package com.dreamhomes.haven.user;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public agent directory endpoint, end-to-end against Postgres.
 *
 * <p>The "missing q" case is intentionally first because it captures a real production
 * regression: a Java {@code null} {@code @RequestParam} binds untyped on the JDBC layer,
 * Postgres picks {@code bytea} as the fallback, and {@code LOWER(?)} fails to resolve to
 * any function overload — so the parser raises before the {@code :q IS NULL} short-circuit
 * ever evaluates. The fix lives in the controller, but the test belongs against a real
 * Postgres (H2/in-memory JPQL handles the null bind differently and would falsely pass).
 */
@AutoConfigureMockMvc
class AgentDirectoryIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;

    @Test
    void missingQueryParamReturnsAllAgentsInsteadOfFiveHundred() throws Exception {
        jwtTestSupport.persistUser(Role.AGENT);
        jwtTestSupport.persistUser(Role.AGENT);

        // No `?q=` at all — the regression case. Must be 200, not 500.
        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].role").value("AGENT"));
    }

    @Test
    void emptyQueryParamBehavesTheSameAsMissingOne() throws Exception {
        jwtTestSupport.persistUser(Role.AGENT);

        // Equivalence check — the frontend's `q=` workaround and a missing q should
        // converge on the same behaviour so the workaround can be retired.
        mockMvc.perform(get("/api/agents?q="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("AGENT"));
    }
}
