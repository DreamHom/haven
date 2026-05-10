package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.AgentProfile;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;

import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow against a real Postgres + the real security filter chain:
 *
 * <ol>
 *   <li>POST /api/auth/register persists a user.</li>
 *   <li>POST /api/auth/login returns a JWT.</li>
 *   <li>GET /api/me with the bearer returns the principal.</li>
 *   <li>GET /api/me without a bearer returns 401.</li>
 *   <li>GET /api/me with a tampered bearer returns 401.</li>
 * </ol>
 *
 * <p>If this test passes, the entire auth slice — entity, service, JWT, filter,
 * security config — is wired correctly. If a future change quietly breaks any link,
 * this fails before any feature endpoint does.
 */
@AutoConfigureMockMvc
class AuthFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AgentProfileRepository agentProfileRepository;

    @Autowired
    JwtTestSupport jwtTestSupport;

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    void cleanUsers() {
        // Run before AND after — non-transactional ITs commit rows that leak otherwise.
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerThenLoginThenAccessProtectedEndpoint() throws Exception {
        String registerBody = """
                {
                  "email": "ada@example.com",
                  "password": "secret-password",
                  "fullName": "Ada Lovelace",
                  "phone": "+2348012345678",
                  "role": "OWNER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("OWNER"));

        String loginBody = """
                {
                  "email": "ada@example.com",
                  "password": "secret-password"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.get("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void protectedEndpointWithoutBearerReturns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithTamperedBearerReturns401() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtTestSupportMintsAcceptableBearerForFutureProtectedEndpointTests() throws Exception {
        User agent = jwtTestSupport.persistUser(Role.AGENT);

        mockMvc.perform(get("/api/me").header("Authorization", jwtTestSupport.bearerFor(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(agent.getEmail()))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void registeringAsAgentPersistsAgentProfile() throws Exception {
        String body = """
                {
                  "email": "agent-e2e@example.com",
                  "password": "secret-password",
                  "fullName": "End-to-End Agent",
                  "role": "AGENT",
                  "licenseNumber": "LIC-E2E-001"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        Optional<AgentProfile> profile = agentProfileRepository.findByLicenseNumber("LIC-E2E-001");
        assertThat(profile).isPresent();
        Optional<User> user = userRepository.findByEmail("agent-e2e@example.com");
        assertThat(user).isPresent();
        assertThat(profile.get().getUserId()).isEqualTo(user.get().getId());
    }

    @Test
    void logoutInvalidatesPreviouslyIssuedToken() throws Exception {
        User user = jwtTestSupport.persistUser(Role.OWNER);
        String bearer = jwtTestSupport.bearerFor(user);

        // Bearer works pre-logout
        mockMvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(status().isOk());

        // Logout
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        // Same bearer is now rejected — token version was bumped
        mockMvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }
}
