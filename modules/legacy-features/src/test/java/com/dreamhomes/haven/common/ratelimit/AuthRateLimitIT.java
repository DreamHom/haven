package com.dreamhomes.haven.common.ratelimit;

import com.dreamhomes.haven.common.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthRateLimitIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    private static final String LOGIN_BODY = """
            { "email": "ratelimit-canary@example.com", "password": "any-password" }
            """;

    @Test
    void sixthLoginAttemptFromSameIpReturns429WithRetryAfter() throws Exception {
        // Use an X-Forwarded-For value unique to this test so we don't share state with siblings.
        String ip = "203.0.113.42";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY))
                    .andExpect(status().isUnauthorized()); // wrong creds, but the request is allowed
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void rateLimitDoesNotApplyToOtherEndpoints() throws Exception {
        String ip = "203.0.113.43";

        // Hammer /api/me many times — it returns 401 (no bearer) each time but should
        // never become 429, because rate limiting only covers the auth endpoints we listed.
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/me").header("X-Forwarded-For", ip))
                    .andExpect(status().isUnauthorized());
        }
    }
}
