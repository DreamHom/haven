package com.dreamhomes.haven.user.account;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTestSupport jwtTestSupport;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AgentProfileRepository agentProfileRepository;

    @Test
    void userCanReadAndPatchOwnSettingsProfile() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        String bearer = jwtTestSupport.bearerFor(owner);

        mockMvc.perform(get("/api/me/profile").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.fullName").value(owner.getFullName()));

        mockMvc.perform(patch("/api/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "updated-owner@example.com",
                                  "displayName": "Updated Owner",
                                  "phone": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated-owner@example.com"))
                .andExpect(jsonPath("$.displayName").value("Updated Owner"))
                .andExpect(jsonPath("$.phone").value(nullValue()));

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("updated-owner@example.com");
        assertThat(reloaded.getDisplayName()).isEqualTo("Updated Owner");
        assertThat(reloaded.getPhone()).isNull();
    }

    @Test
    void passwordChangeInvalidatesExistingBearer() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        String bearer = jwtTestSupport.bearerFor(owner);

        mockMvc.perform(post("/api/me/password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "test-password-123",
                                  "newPassword": "brand-new-password-123"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agentLicenseChangeClearsCredentialVerification() throws Exception {
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber("LIC-OLD")
                .agency("Old Agency")
                .credentialVerifiedAt(Instant.parse("2026-05-10T00:00:00Z"))
                .build());
        String bearer = jwtTestSupport.bearerFor(agent);

        mockMvc.perform(patch("/api/me/agent-profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licenseNumber": "LIC-NEW",
                                  "agency": "New Agency"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber").value("LIC-NEW"))
                .andExpect(jsonPath("$.agency").value("New Agency"))
                .andExpect(jsonPath("$.agentCredentialVerifiedAt").value(nullValue()));

        AgentProfile reloaded = agentProfileRepository.findById(agent.getId()).orElseThrow();
        assertThat(reloaded.getLicenseNumber()).isEqualTo("LIC-NEW");
        assertThat(reloaded.getAgency()).isEqualTo("New Agency");
        assertThat(reloaded.getCredentialVerifiedAt()).isNull();
    }
}
