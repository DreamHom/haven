package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.auth.service.AuthService;
/**
 * End-to-end suspend → revoked-token → re-login-blocked → reactivate flow:
 *
 * <ol>
 *   <li>Owner authenticates and gets a working JWT.</li>
 *   <li>Admin suspends the owner. The suspend bumps {@code tokenVersion}; the existing
 *       JWT becomes useless on the very next request.</li>
 *   <li>Owner tries to log in fresh — blocked at {@code AuthService.login}.</li>
 *   <li>Admin reactivates. Owner can log in again.</li>
 * </ol>
 */
@AutoConfigureMockMvc
class AdminUserModerationIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;


    @Test
    void adminSuspendsOwnerThenOwnerCannotSubmitVerificationOrLogin() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);
        String ownerBearer = jwtTestSupport.bearerFor(owner);

        // Pre-suspension: owner can authenticate.
        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "X" }
                                }
                                """))
                .andExpect(status().isCreated());

        // Admin suspends.
        mockMvc.perform(post("/api/admin/users/" + owner.getId() + "/suspend")
                        .header("Authorization", jwtTestSupport.bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Repeated policy violations" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedAt").exists());

        // Owner's outstanding token now mismatches tokenVersion → 401.
        mockMvc.perform(post("/api/verifications")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "Y" }
                                }
                                """))
                .andExpect(status().isUnauthorized());

        // Owner re-login attempt — blocked at AuthService.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(owner.getEmail(), JwtTestSupport.DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized());

        // DB state: tokenVersion bumped, suspendedAt stamped.
        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getSuspendedAt()).isNotNull();
        assertThat(reloaded.getTokenVersion()).isGreaterThan(owner.getTokenVersion());

        // Audit row exists.
        assertThat(auditLogRepository.findAll())
                .anyMatch(row -> row.getAction() == AdminAction.USER_SUSPENDED
                        && row.getTargetId().equals(owner.getId()));
    }

    @Test
    void reactivateRestoresLoginAbility() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);

        mockMvc.perform(post("/api/admin/users/" + owner.getId() + "/suspend")
                        .header("Authorization", jwtTestSupport.bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "x" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/" + owner.getId() + "/reactivate")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedAt").doesNotExist());

        // Login works again now that suspendedAt is cleared.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(owner.getEmail(), JwtTestSupport.DEFAULT_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotSuspendThemselves() throws Exception {
        User admin = jwtTestSupport.persistUser(Role.ADMIN);

        mockMvc.perform(post("/api/admin/users/" + admin.getId() + "/suspend")
                        .header("Authorization", jwtTestSupport.bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "anything" }
                                """))
                .andExpect(status().isForbidden());
    }
}
