package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.auth.JwtTestSupport;
import com.dreamhomes.haven.common.AbstractPostgresIT;
import com.dreamhomes.haven.notification.Notification;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.notification.NotificationSource;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.dreamhomes.haven.verification.Verification;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.VerificationStatus;
import com.dreamhomes.haven.verification.VerificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end admin decision flow:
 * <ol>
 *   <li>Owner submits OWNER_IDENTITY.</li>
 *   <li>Admin lists pending — sees the row.</li>
 *   <li>Admin approves — owner's identity badge stamps, audit row writes, owner gets a sync notification.</li>
 * </ol>
 *
 * <p>Plus a parallel rejection flow that asserts the rejection notification carries
 * the reason all the way to the submitter.
 */
@AutoConfigureMockMvc
class AdminVerificationDecisionIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        verificationRepository.deleteAll();
        propertyRepository.deleteAll();
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminApprovesOwnerIdentityFlipsBadgeWritesAuditAndNotifiesSubmitter() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);

        // 1. Owner submits.
        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "AB1234567" }
                                }
                                """))
                .andExpect(status().isCreated());

        Long verificationId = verificationRepository.findAll().get(0).getId();

        // 2. Admin lists the queue.
        mockMvc.perform(get("/api/admin/verifications?type=OWNER_IDENTITY")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(verificationId))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        // 3. Admin approves.
        mockMvc.perform(post("/api/admin/verifications/" + verificationId + "/approve")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedByAdminId").value(admin.getId()));

        // 4. Verification row terminal.
        Verification verified = verificationRepository.findById(verificationId).orElseThrow();
        assertThat(verified.getStatus()).isEqualTo(VerificationStatus.APPROVED);

        // 5. Owner's identity badge flipped.
        User stampedOwner = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(stampedOwner.getIdentityVerifiedAt()).isNotNull();

        // 6. Audit row exists with the right shape.
        List<AdminAuditLog> auditRows = auditLogRepository.findAll();
        assertThat(auditRows).hasSize(1);
        assertThat(auditRows.get(0).getAdminId()).isEqualTo(admin.getId());
        assertThat(auditRows.get(0).getAction()).isEqualTo(AdminAction.VERIFICATION_APPROVED);
        assertThat(auditRows.get(0).getTargetType()).isEqualTo(AuditTargetType.VERIFICATION);
        assertThat(auditRows.get(0).getTargetId()).isEqualTo(verificationId);

        // 7. Owner has a sync notification waiting.
        List<Notification> notifs = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(owner.getId());
        assertThat(notifs).hasSize(1);
        assertThat(notifs.get(0).getKind()).isEqualTo(NotificationKind.VERIFICATION_APPROVED);
        assertThat(notifs.get(0).getSource()).isEqualTo(NotificationSource.SYNC);
        assertThat(notifs.get(0).getEventId()).isNull();
    }

    @Test
    void adminRejectsCarriesReasonIntoTheNotification() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "AB1234567" }
                                }
                                """))
                .andExpect(status().isCreated());
        Long verificationId = verificationRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/admin/verifications/" + verificationId + "/reject")
                        .header("Authorization", jwtTestSupport.bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Document image is unreadable" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Owner remains unverified.
        assertThat(userRepository.findById(owner.getId()).orElseThrow().getIdentityVerifiedAt()).isNull();

        Notification notif = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(owner.getId()).get(0);
        assertThat(notif.getKind()).isEqualTo(NotificationKind.VERIFICATION_REJECTED);
        assertThat(notif.getPayload()).contains("Document image is unreadable");
    }

    @Test
    void doubleApproveOnSameRowReturns409Conflict() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);

        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "X" }
                                }
                                """))
                .andExpect(status().isCreated());
        Long verificationId = verificationRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/admin/verifications/" + verificationId + "/approve")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/verifications/" + verificationId + "/approve")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isConflict());
    }
}
