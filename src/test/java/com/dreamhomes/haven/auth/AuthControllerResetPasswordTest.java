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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.auth.controller.AuthController;
import com.dreamhomes.haven.auth.passwordreset.PasswordResetService;
import com.dreamhomes.haven.auth.service.AuthService;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.user.service.UserCredentialsService;

/**
 * Reset-password is a thin 204 endpoint; the important contract for cookie-first clients
 * is that the httpOnly session cookie is cleared after a successful reset.
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
class AuthControllerResetPasswordTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @MockBean
    PasswordResetService passwordResetService;

    @MockBean
    JwtService jwtService;

    @MockBean
    com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    UserCredentialsService userCredentialsService;

    @MockBean
    com.dreamhomes.haven.auth.cookie.JwtCookieService jwtCookieService;

    @Test
    void resetPasswordReturns204AndClearsJwtCookie() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token-here",
                                  "newPassword": "NewValidPassword9!"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(passwordResetService).resetWithToken("reset-token-here", "NewValidPassword9!");
        verify(jwtCookieService).clearTokenCookie(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetPasswordInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).resetWithToken(anyString(), anyString());
    }
}
