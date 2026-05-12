package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import com.dreamhomes.haven.auth.controller.AuthController;
import com.dreamhomes.haven.auth.exception.InvalidCredentialsException;
import com.dreamhomes.haven.auth.service.AuthService;
import com.dreamhomes.haven.auth.service.JwtService;

/**
 * Login is a thinner controller than register: success returns a token, the only
 * domain failure (invalid credentials) maps to 401. Per-validator behavior lives in
 * the validator unit tests; one smoke test for @Valid wiring is enough.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class AuthControllerLoginTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @MockBean
    JwtService jwtService;

    @MockBean
    com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    com.dreamhomes.haven.user.service.UserCredentialsService userCredentialsService;

    @Test
    void successfulLoginReturns200WithTokenInBody() throws Exception {
        when(authService.login(any())).thenReturn(new com.dreamhomes.haven.auth.dto.LoginResult(
                "jwt-token-value", 7L,
                com.dreamhomes.haven.user.model.Role.OWNER, "Ada Lovelace", 3600L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ada@example.com",
                                  "password": "secret-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt-token-value")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresInSeconds", is(3600)))
                .andExpect(jsonPath("$.userId", is(7)))
                .andExpect(jsonPath("$.role", is("OWNER")))
                .andExpect(jsonPath("$.fullName", is("Ada Lovelace")));
    }

    @Test
    void invalidCredentialsExceptionMapsTo401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ada@example.com",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestBodyReturns400() throws Exception {
        // Smoke test that @Valid is wired on the login method.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "secret-password"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
