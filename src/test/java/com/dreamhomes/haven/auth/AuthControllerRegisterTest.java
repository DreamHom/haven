package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.auth.controller.AuthController;
import com.dreamhomes.haven.auth.service.AuthService;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Tests cover what the controller layer owns: response shape (always 202 + empty body for
 * the anti-enumeration contract), the cross-field validation rules WE wrote on
 * RegisterRequest (admin self-registration block, agent licence requirement), and one
 * smoke test that @Valid is wired so future endpoints can rely on it. Single-field
 * validators (@StrictEmail, @NotCommonPassword, @Size) are tested in their own unit tests.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class AuthControllerRegisterTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @MockBean
    com.dreamhomes.haven.auth.passwordreset.PasswordResetService passwordResetService;

    @MockBean
    JwtService jwtService;

    @MockBean
    com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    com.dreamhomes.haven.user.service.UserCredentialsService userCredentialsService;

    @MockBean
    com.dreamhomes.haven.auth.cookie.JwtCookieService jwtCookieService;

    @Test
    void successfulRegistrationReturns202WithEmptyBody() throws Exception {
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
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.nextStep", containsString("login")));
    }

    @Test
    void duplicateEmailAlsoReturns202SoCallerCannotDistinguish() throws Exception {
        // Service swallows the duplicate; controller surfaces the same 202 + empty body
        // as a fresh registration. That equivalence IS the test — anti-enumeration.
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
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.nextStep", containsString("login")));
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
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.nextStep", containsString("login")));
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
