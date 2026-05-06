package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerRegisterTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AuthService authService;

    @MockBean
    JwtService jwtService;

    @Test
    void validApplicantRegistrationReturns201WithUserId() throws Exception {
        when(authService.register(any())).thenAnswer(inv -> User.builder()
                .id(42L)
                .email("ada@example.com")
                .role(Role.APPLICANT)
                .fullName("Ada Lovelace")
                .createdAt(Instant.now())
                .build());

        String body = """
                {
                  "email": "ada@example.com",
                  "password": "secret-password",
                  "fullName": "Ada Lovelace",
                  "phone": "+2348012345678",
                  "role": "APPLICANT"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(42)))
                .andExpect(jsonPath("$.email", is("ada@example.com")))
                .andExpect(jsonPath("$.role", is("APPLICANT")));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyRegisteredException("dup@example.com"));

        String body = """
                {
                  "email": "dup@example.com",
                  "password": "secret-password",
                  "fullName": "Dup User",
                  "role": "OWNER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void missingEmailReturns400AndDoesNotCallService() throws Exception {
        String body = """
                {
                  "password": "secret-password",
                  "fullName": "No Email",
                  "role": "OWNER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
        String body = """
                {
                  "email": "shortpw@example.com",
                  "password": "short",
                  "fullName": "Short Pw",
                  "role": "OWNER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailFormatReturns400() throws Exception {
        String body = """
                {
                  "email": "not-an-email",
                  "password": "secret-password",
                  "fullName": "Bad Email",
                  "role": "OWNER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminRoleSelfRegistrationIsRejectedWith400() throws Exception {
        // PRD: admins are seeded only — never self-register.
        String body = """
                {
                  "email": "wannabeadmin@example.com",
                  "password": "secret-password",
                  "fullName": "Wannabe Admin",
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }
}
