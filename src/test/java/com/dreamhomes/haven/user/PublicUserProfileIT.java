package com.dreamhomes.haven.user;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;

/**
 * Public profile endpoint, end-to-end: anyone can hit it without a JWT, the response
 * never includes private contact info, and the verified badge stamps are surfaced as
 * soon as an admin approves the corresponding verification.
 */
@AutoConfigureMockMvc
class PublicUserProfileIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;


    @Test
    void anonymousVisitorCanReadOwnerProfileAndNeverSeesPrivateContactDetails() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);

        mockMvc.perform(get("/api/users/" + owner.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.getId()))
                .andExpect(jsonPath("$.fullName").value(owner.getFullName()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.suspended").value(false))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist())
                .andExpect(header().string("Cache-Control", containsString("public")));
    }

    @Test
    void publicProfileReflectsAdminApprovedIdentityBadge() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);

        // Owner submits identity verification, admin approves, public profile flips.
        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "X" } }
                                """))
                .andExpect(status().isCreated());
        Long verificationId = verificationRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/admin/verifications/" + verificationId + "/approve")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + owner.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityVerifiedAt").exists());
    }

    @Test
    void agentProfileExposesCredentialBadgeOnlyAfterApproval() throws Exception {
        // Persist agent + agent_profile (registration normally does both).
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId()).licenseNumber("LIC-" + agent.getId())
                .createdAt(Instant.now()).build());

        mockMvc.perform(get("/api/users/" + agent.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.agentCredentialVerifiedAt")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unknownUserIdReturns404() throws Exception {
        mockMvc.perform(get("/api/users/999999/profile"))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentProfileSurfacesServiceAreasLanguagesSpecializationsAndFeeSchedule() throws Exception {
        // Public discovery fields per PRD §4.2 ("Agent profiles are fully transparent — fees,
        // ratings, deals closed, specializations, locations covered"). The frontend's agent-
        // profile page needs these visible without a second authenticated call.
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber("LIC-" + agent.getId())
                .serviceAreas(java.util.List.of("Lekki", "Yaba", "Victoria Island"))
                .languages(java.util.List.of("English", "Yoruba"))
                .specializationTags(java.util.List.of("luxury", "rentals"))
                .feeSchedule("5% on sale, 1 month rent commission")
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/users/" + agent.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAreas").isArray())
                .andExpect(jsonPath("$.serviceAreas[0]").value("Lekki"))
                .andExpect(jsonPath("$.serviceAreas.length()").value(3))
                .andExpect(jsonPath("$.languages[1]").value("Yoruba"))
                .andExpect(jsonPath("$.specializationTags[0]").value("luxury"))
                .andExpect(jsonPath("$.feeSchedule")
                        .value("5% on sale, 1 month rent commission"));
    }

    @Test
    void agentProfileWithoutDiscoveryFieldsReturnsEmptyArraysAndNullFeeSchedule() throws Exception {
        // An agent who registered before setting the discovery fields (or just left them empty)
        // should still produce a valid response shape — empty arrays, not null, so the FE
        // doesn't have to null-check every field before mapping.
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId())
                .licenseNumber("LIC-" + agent.getId())
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/users/" + agent.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAreas").isArray())
                .andExpect(jsonPath("$.serviceAreas.length()").value(0))
                .andExpect(jsonPath("$.languages.length()").value(0))
                .andExpect(jsonPath("$.specializationTags.length()").value(0))
                .andExpect(jsonPath("$.feeSchedule")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void ownerProfileOmitsAgentDiscoveryFieldsViaEmptyArrays() throws Exception {
        // Discovery fields are agent-specific; an owner profile carries the same JSON keys
        // for shape stability but they read as empty / null. Keeps the FE renderer uniform.
        User owner = jwtTestSupport.persistUser(Role.OWNER);

        mockMvc.perform(get("/api/users/" + owner.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAreas.length()").value(0))
                .andExpect(jsonPath("$.languages.length()").value(0))
                .andExpect(jsonPath("$.specializationTags.length()").value(0))
                .andExpect(jsonPath("$.feeSchedule")
                        .value(org.hamcrest.Matchers.nullValue()));
    }
}
