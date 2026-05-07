package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.auth.JwtTestSupport;
import com.dreamhomes.haven.comment.CommentRepository;
import com.dreamhomes.haven.common.AbstractPostgresIT;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.notification.Notification;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.PropertyType;
import com.dreamhomes.haven.user.AgentProfile;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full agent–listing handshake flow:
 *
 * <ol>
 *   <li>Owner has a LIVE listing.</li>
 *   <li>Owner invites an agent → agent gets a sync notification.</li>
 *   <li>Agent accepts → owner gets a sync notification; partial UQ on ACCEPTED holds.</li>
 *   <li>Owner tries to invite a second agent → 409 Conflict.</li>
 *   <li>Agent revokes (resigns) → owner notified; second invite now succeeds.</li>
 * </ol>
 */
@AutoConfigureMockMvc
class AgentListingFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired AgentListingRepository agentListingRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        // FK order matters across siblings: clear agent_listings + admin_audit_log +
        // comments + verifications + notifications BEFORE listings/properties/users.
        agentListingRepository.deleteAll();
        auditLogRepository.deleteAll();
        commentRepository.deleteAll();
        verificationRepository.deleteAll();
        notificationRepository.deleteAll();
        listingRepository.deleteAll();
        propertyRepository.deleteAll();
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerInvitesAgentAcceptsSecondInviteBlockedAgentResignsThenNewInviteSucceeds() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User agentA = persistAgent();
        User agentB = persistAgent();
        Long listingId = persistLiveListingFor(owner.getId());

        // 1. Owner invites agent A.
        mockMvc.perform(post("/api/listings/" + listingId + "/agent-assignment")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentA.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        Long assignmentId = agentListingRepository.findAll().get(0).getId();

        // Agent A gets a sync notification.
        List<Notification> agentNotifs = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(agentA.getId());
        assertThat(agentNotifs).hasSize(1);
        assertThat(agentNotifs.get(0).getKind()).isEqualTo(NotificationKind.AGENT_ASSIGNMENT_REQUESTED);

        // 2. Owner can't invite a second agent while one is pending.
        mockMvc.perform(post("/api/listings/" + listingId + "/agent-assignment")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentB.getId() + "}"))
                .andExpect(status().isConflict());

        // 3. Agent A accepts.
        mockMvc.perform(post("/api/agent-listings/" + assignmentId + "/accept")
                        .header("Authorization", jwtTestSupport.bearerFor(agentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Owner gets the accept notification.
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(owner.getId()))
                .anyMatch(n -> n.getKind() == NotificationKind.AGENT_ASSIGNMENT_ACCEPTED);

        // 4. Even after acceptance, owner can't double-assign.
        mockMvc.perform(post("/api/listings/" + listingId + "/agent-assignment")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentB.getId() + "}"))
                .andExpect(status().isConflict());

        // 5. Agent A resigns.
        mockMvc.perform(post("/api/agent-listings/" + assignmentId + "/revoke")
                        .header("Authorization", jwtTestSupport.bearerFor(agentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Stepping down\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        // Owner notified of the resignation.
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(owner.getId()))
                .anyMatch(n -> n.getKind() == NotificationKind.AGENT_ASSIGNMENT_REVOKED);

        // 6. Owner can now invite agent B.
        mockMvc.perform(post("/api/listings/" + listingId + "/agent-assignment")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentB.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void agentDeclinesAndOwnerCanInviteAnotherAgent() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User agentA = persistAgent();
        User agentB = persistAgent();
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/agent-assignment")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentA.getId() + "}"))
                .andExpect(status().isCreated());
        Long assignmentId = agentListingRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/agent-listings/" + assignmentId + "/decline")
                        .header("Authorization", jwtTestSupport.bearerFor(agentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Booked solid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        // Decline → terminal but doesn't lock the listing — owner immediately invites agent B.
        mockMvc.perform(post("/api/listings/" + listingId + "/agent-assignment")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentB.getId() + "}"))
                .andExpect(status().isCreated());

        // Owner has notifications for the decline AND the new invite-success doesn't notify owner —
        // notifications go to the recipients of each transition.
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(owner.getId()))
                .anyMatch(n -> n.getKind() == NotificationKind.AGENT_ASSIGNMENT_DECLINED);
    }

    @Test
    void thirdPartyAgentCannotAcceptOnSomeoneElsesAssignment() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User agentA = persistAgent();
        User agentB = persistAgent();
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/agent-assignment")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentA.getId() + "}"))
                .andExpect(status().isCreated());
        Long assignmentId = agentListingRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/agent-listings/" + assignmentId + "/accept")
                        .header("Authorization", jwtTestSupport.bearerFor(agentB)))
                .andExpect(status().isForbidden());
    }

    private User persistAgent() {
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        agentProfileRepository.save(AgentProfile.builder()
                .userId(agent.getId()).licenseNumber("LIC-" + agent.getId())
                .createdAt(Instant.now()).build());
        return agent;
    }

    private Long persistLiveListingFor(Long ownerId) {
        Property property = propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("Plot 5").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        Listing listing = listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        return listing.getId();
    }
}
