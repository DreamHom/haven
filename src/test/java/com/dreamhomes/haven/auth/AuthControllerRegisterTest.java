package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests cover what the controller layer owns: response shape on success, exception
 * mapping on failure, the cross-field validation rules WE wrote on RegisterRequest
 * (admin self-registration block, agent licence requirement), and one smoke test that
 * @Valid is wired so future endpoints can rely on it. Single-field validators
 * (@StrictEmail, @NotCommonPassword, @Size) are tested in their own unit tests.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class AuthControllerRegisterTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @MockBean
    JwtService jwtService;

    @MockBean
    com.dreamhomes.haven.user.UserCredentialsService userCredentialsService;

    @Test
    void successfulRegistrationReturns201WithUserSummary() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(
                42L, "ada@example.com", "Ada Lovelace", Role.APPLICANT, Instant.now()));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ada@example.com",
                                  "password": "secret-password",
                                  "fullName": "Ada Lovelace",
                                  "phone": "+2348012345678",
                                  "role": "APPLICANT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(42)))
                .andExpect(jsonPath("$.email", is("ada@example.com")))
                .andExpect(jsonPath("$.role", is("APPLICANT")));
    }

    @Test
    void duplicateEmailReturns409WithoutEchoingTheEmail() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dup@example.com",
                                  "password": "secret-password",
                                  "fullName": "Dup User",
                                  "role": "OWNER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("dup@example.com"))));
    }

    @Test
    void invalidRequestBodyReturns400WithoutCallingTheService() throws Exception {
        // Smoke test that @Valid is wired on the controller method. Any validation rule
        // suffices — pick missing email. Per-validator behavior is covered by the
        // validators' own unit tests, not duplicated here.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "secret-password",
                                  "fullName": "No Email",
                                  "role": "OWNER"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void agentRoleWithoutLicenceNumberIsRejected() throws Exception {
        // Cross-field rule we wrote on RegisterRequest.isAgentLicenseProvidedWhenNeeded.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "agent@example.com",
                                  "password": "secret-password",
                                  "fullName": "An Agent",
                                  "role": "AGENT"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void agentRoleWithLicenceNumberIsAccepted() throws Exception {
        when(authService.register(any())).thenReturn(new UserResponse(
                99L, "agent@example.com", "An Agent", Role.AGENT, Instant.now()));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "agent@example.com",
                                  "password": "secret-password",
                                  "fullName": "An Agent",
                                  "role": "AGENT",
                                  "licenseNumber": "LIC-12345"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("AGENT")));
    }

    @Test
    void adminRoleSelfRegistrationIsRejected() throws Exception {
        // Cross-field rule we wrote on RegisterRequest.isPublicRole — PRD: admin is seeded only.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "wannabeadmin@example.com",
                                  "password": "secret-password",
                                  "fullName": "Wannabe Admin",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }
}
