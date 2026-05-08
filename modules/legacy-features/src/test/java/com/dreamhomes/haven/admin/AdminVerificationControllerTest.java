package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserRepository;
import com.dreamhomes.haven.verification.Verification;
import com.dreamhomes.haven.verification.VerificationStatus;
import com.dreamhomes.haven.verification.VerificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminVerificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class AdminVerificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminVerificationService adminVerificationService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    @Test
    void adminListsPendingByTypeReturnsPagedResults() throws Exception {
        Page<Verification> page = new PageImpl<>(
                List.of(pending(1L, VerificationType.OWNER_IDENTITY)),
                PageRequest.of(0, 20), 1);
        when(adminVerificationService.listPending(eq(VerificationType.OWNER_IDENTITY), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/verifications?type=OWNER_IDENTITY")
                        .with(asPrincipal(7L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")));
    }

    @Test
    void nonAdminCannotListVerificationsProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(get("/api/admin/verifications?type=OWNER_IDENTITY")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isForbidden());

        verify(adminVerificationService, never()).listPending(any(), any());
    }

    @Test
    void adminApprovesVerificationReturns200WithApprovedRow() throws Exception {
        Verification approved = pending(99L, VerificationType.OWNER_IDENTITY);
        approved.setStatus(VerificationStatus.APPROVED);
        approved.setDecidedByAdminId(7L);
        approved.setDecidedAt(Instant.now());
        when(adminVerificationService.approve(eq(7L), eq(99L), any()))
                .thenReturn(approved);

        mockMvc.perform(post("/api/admin/verifications/99/approve")
                        .with(asPrincipal(7L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.decidedByAdminId", is(7)));
    }

    @Test
    void adminRejectsRequiresReasonReturns400WhenMissing() throws Exception {
        mockMvc.perform(post("/api/admin/verifications/99/reject")
                        .with(asPrincipal(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "" }
                                """))
                .andExpect(status().isBadRequest());

        verify(adminVerificationService, never()).reject(any(), any(), any());
    }

    @Test
    void adminRejectsWithReasonReturns200WithRejectedRow() throws Exception {
        Verification rejected = pending(99L, VerificationType.OWNER_IDENTITY);
        rejected.setStatus(VerificationStatus.REJECTED);
        rejected.setDecisionReason("Image is blurry");
        rejected.setDecidedByAdminId(7L);
        rejected.setDecidedAt(Instant.now());
        when(adminVerificationService.reject(eq(7L), eq(99L), eq("Image is blurry")))
                .thenReturn(rejected);

        mockMvc.perform(post("/api/admin/verifications/99/reject")
                        .with(asPrincipal(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Image is blurry" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.decisionReason", is("Image is blurry")));
    }

    private static Verification pending(Long id, VerificationType type) {
        return Verification.builder()
                .id(id).type(type)
                .submitterUserId(50L).targetUserId(50L)
                .status(VerificationStatus.PENDING)
                .documentRefs("{}")
                .submittedAt(Instant.now())
                .build();
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
