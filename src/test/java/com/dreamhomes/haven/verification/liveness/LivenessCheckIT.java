package com.dreamhomes.haven.verification.liveness;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (HTTP + JPA + Postgres) coverage for the Item 19 mocked liveness flow:
 * run a liveness check, attach the id to a verification submission, and confirm the
 * row is stamped consumed so a replay surfaces as 409.
 */
@AutoConfigureMockMvc
class LivenessCheckIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired LivenessCheckResultRepository livenessRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void mockedLivenessCheckEndpointReturnsPassedWithExplicitMockedFlag() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);

        mockMvc.perform(post("/api/verifications/liveness-check")
                        .header("Authorization", jwtTestSupport.bearerFor(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.score").value(0.97))
                .andExpect(jsonPath("$.provider").value("MOCK"))
                .andExpect(jsonPath("$._mocked").value(true));

        List<LivenessCheckResult> rows = livenessRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUserId()).isEqualTo(owner.getId());
        assertThat(rows.get(0).getConsumedAt()).isNull();
    }

    @Test
    void submittingWithLivenessIdStampsConsumedAndReplayReturns409() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        String bearer = jwtTestSupport.bearerFor(owner);

        MvcResult livenessResult = mockMvc.perform(post("/api/verifications/liveness-check")
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode liveness = objectMapper.readTree(livenessResult.getResponse().getContentAsString());
        long livenessId = liveness.get("id").asLong();

        String submitBody = """
                {
                  "type": "OWNER_IDENTITY",
                  "documentRefs": { "kind": "NIN", "ref": "AB1234567" },
                  "livenessCheckId": %d
                }
                """.formatted(livenessId);

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isCreated());

        LivenessCheckResult consumed = livenessRepository.findById(livenessId).orElseThrow();
        assertThat(consumed.getConsumedAt()).isNotNull();

        // Submitting again with the same liveness id surfaces 409 — replay protection
        // is real even against direct API misuse (Vista may tap-tap before disabling).
        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isConflict());
    }

    @Test
    void submittingWithForeignLivenessIdReturns403() throws Exception {
        User ownerA = jwtTestSupport.persistUser(Role.OWNER);
        User ownerB = jwtTestSupport.persistUser(Role.OWNER);

        MvcResult result = mockMvc.perform(post("/api/verifications/liveness-check")
                        .header("Authorization", jwtTestSupport.bearerFor(ownerA)))
                .andExpect(status().isCreated())
                .andReturn();
        long livenessIdForOwnerA = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // Owner B tries to submit using Owner A's liveness id — must 403.
        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "AB1234567" },
                                  "livenessCheckId": %d
                                }
                                """.formatted(livenessIdForOwnerA)))
                .andExpect(status().isForbidden());
    }
}
