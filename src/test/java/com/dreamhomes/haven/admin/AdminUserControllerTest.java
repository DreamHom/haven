package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.dto.UserAdminView;
import com.dreamhomes.haven.user.service.UserCredentialsService;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.admin.controller.AdminUserController;
import com.dreamhomes.haven.admin.service.AdminUserService;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminUserService adminUserService;
    @MockBean JwtService jwtService;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void adminSuspendsUserReturns200WithSuspendedAtSet() throws Exception {
        UserAdminView suspended = view(50L, Role.OWNER, Instant.now());
        when(adminUserService.suspend(eq(7L), eq(50L), eq("policy violation"))).thenReturn(suspended);

        mockMvc.perform(post("/api/admin/users/50/suspend")
                        .with(asPrincipal(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "policy violation" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(50)))
                .andExpect(jsonPath("$.suspendedAt", is(notNullValue())));
    }

    @Test
    void agentCannotSuspendUserProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/admin/users/50/suspend")
                        .with(asPrincipal(99L, Role.AGENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "anything" }
                                """))
                .andExpect(status().isForbidden());

        verify(adminUserService, never()).suspend(any(), any(), any());
    }

    @Test
    void suspendWithoutReasonReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/users/50/suspend")
                        .with(asPrincipal(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminReactivatesUserReturns200WithSuspendedAtCleared() throws Exception {
        UserAdminView reactivated = view(50L, Role.OWNER, null);
        when(adminUserService.reactivate(eq(7L), eq(50L))).thenReturn(reactivated);

        mockMvc.perform(post("/api/admin/users/50/reactivate")
                        .with(asPrincipal(7L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedAt", is(nullValue())));
    }

    private static UserAdminView view(Long id, Role role, Instant suspendedAt) {
        return new UserAdminView(id, "u@x", role, suspendedAt, null);
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
