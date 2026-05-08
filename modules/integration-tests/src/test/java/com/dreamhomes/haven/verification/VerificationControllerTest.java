package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VerificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class VerificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean VerificationService verificationService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    @Test
    void ownerSubmitsIdentityVerificationReturns201WithSummary() throws Exception {
        when(verificationService.submit(eq(50L), any(SubmitVerificationCommand.class)))
                .thenAnswer(inv -> stub(99L, VerificationType.OWNER_IDENTITY));

        mockMvc.perform(post("/api/verifications")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "AB1234" }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(99)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.type", is("OWNER_IDENTITY")));
    }

    @Test
    void adminCannotSubmitVerificationProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/verifications")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OWNER_IDENTITY",
                                  "documentRefs": { "kind": "NIN", "ref": "AB1234" }
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(verificationService, never()).submit(any(), any());
    }

    @Test
    void emptyDocumentRefsReturns400() throws Exception {
        mockMvc.perform(post("/api/verifications")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OWNER_IDENTITY", "documentRefs": {} }
                                """))
                .andExpect(status().isBadRequest());

        verify(verificationService, never()).submit(any(), any());
    }

    @Test
    void missingTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/verifications")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "documentRefs": { "kind": "NIN", "ref": "x" } }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static Verification stub(Long id, VerificationType type) {
        return Verification.builder()
                .id(id).type(type)
                .status(VerificationStatus.PENDING)
                .submitterUserId(50L).targetUserId(50L)
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
