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

    @Test
    void agentSetsDiscoveryFieldsViaPatchThenReadsThemBackOnPrivateAndPublicProfile() throws Exception {
        // Agent self-edits the four public-discovery fields (PRD §4.2: fees, specializations,
        // locations covered). The PATCH echoes the new state on the private settings DTO;
        // the public profile endpoint surfaces the same values for any anonymous visitor.
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber("LIC-" + agent.getId())
                .createdAt(Instant.now())
                .build());
        String bearer = jwtTestSupport.bearerFor(agent);

        mockMvc.perform(patch("/api/me/agent-profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceAreas": ["Lekki", "Yaba", "VI"],
                                  "languages": ["English", "Yoruba"],
                                  "specializationTags": ["luxury", "rentals"],
                                  "feeSchedule": "5% on sale, 1 month rent commission"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAreas[0]").value("Lekki"))
                .andExpect(jsonPath("$.serviceAreas.length()").value(3))
                .andExpect(jsonPath("$.languages[1]").value("Yoruba"))
                .andExpect(jsonPath("$.specializationTags[0]").value("luxury"))
                .andExpect(jsonPath("$.feeSchedule")
                        .value("5% on sale, 1 month rent commission"));

        // Sibling check — the public-projection endpoint sees the same values, proving
        // the write reached the row and the public read goes through the same column.
        mockMvc.perform(get("/api/users/" + agent.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAreas[2]").value("VI"))
                .andExpect(jsonPath("$.feeSchedule")
                        .value("5% on sale, 1 month rent commission"));
    }

    @Test
    void omittedDiscoveryFieldsInPatchDoNotClearExistingValues() throws Exception {
        // null = "no change" semantic. A PATCH that touches only feeSchedule must leave
        // the array fields alone — critical for partial-update UX where the FE only
        // sends what changed.
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber("LIC-" + agent.getId())
                .serviceAreas(java.util.List.of("Lekki"))
                .languages(java.util.List.of("English"))
                .createdAt(Instant.now())
                .build());
        String bearer = jwtTestSupport.bearerFor(agent);

        mockMvc.perform(patch("/api/me/agent-profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "feeSchedule": "₦200k consultation" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAreas[0]").value("Lekki"))
                .andExpect(jsonPath("$.languages[0]").value("English"))
                .andExpect(jsonPath("$.feeSchedule").value("₦200k consultation"));
    }

    @Test
    void emptyArrayInPatchExplicitlyClearsThatField() throws Exception {
        // [] = "clear it" — distinct from null. The FE should send [] when the agent
        // removes every tag, not omit the field.
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber("LIC-" + agent.getId())
                .specializationTags(java.util.List.of("luxury", "rentals"))
                .createdAt(Instant.now())
                .build());
        String bearer = jwtTestSupport.bearerFor(agent);

        mockMvc.perform(patch("/api/me/agent-profile")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "specializationTags": [] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specializationTags.length()").value(0));
    }
}
