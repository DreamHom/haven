package com.dreamhomes.haven.user.account;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.user.account.controller.MeController;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.dto.PrivateUserProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserAccountService;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class MeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserAccountService userAccountService;

    @MockBean
    JwtService jwtService;

    @MockBean
    com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    UserCredentialsService userCredentialsService;

    @Test
    void authenticatedCallerCanFetchPrivateSettingsProfile() throws Exception {
        when(userAccountService.findMyProfile(7L)).thenReturn(profile(Role.OWNER));

        mockMvc.perform(get("/api/me/profile").with(asPrincipal(7L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("owner@example.com")))
                .andExpect(jsonPath("$.licenseNumber", nullValue()));
    }

    @Test
    void patchMyProfileUpdatesAuthenticatedUserOnly() throws Exception {
        when(userAccountService.updateMyProfile(eq(7L), eq("new@example.com"),
                eq("Ada Lovelace"), eq("Ada"), eq("+2348000000000"), eq(null), eq(null), eq(null)))
                .thenReturn(profile(Role.OWNER));

        mockMvc.perform(patch("/api/me")
                        .with(asPrincipal(7L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "fullName": "Ada Lovelace",
                                  "displayName": "Ada",
                                  "phone": "+2348000000000"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPatchBodyReturns400() throws Exception {
        mockMvc.perform(patch("/api/me")
                        .with(asPrincipal(7L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validPasswordChangeReturns204() throws Exception {
        mockMvc.perform(post("/api/me/password")
                        .with(asPrincipal(7L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "old-password",
                                  "newPassword": "new-password-123"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userAccountService).changePassword(7L, "old-password", "new-password-123");
    }

    @Test
    void uploadAvatarReturns200() throws Exception {
        when(userAccountService.uploadMyAvatar(eq(7L), any())).thenReturn(profile(Role.OWNER));

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .with(asPrincipal(7L, Role.OWNER)))
                .andExpect(status().isOk());

        verify(userAccountService).uploadMyAvatar(eq(7L), any());
    }

    @Test
    void ownerCannotHitAgentOnlyProfileEndpoint() throws Exception {
        mockMvc.perform(patch("/api/me/agent-profile")
                        .with(asPrincipal(7L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "licenseNumber": "LIC-2" }
                                """))
                .andExpect(status().isForbidden());

        verify(userAccountService, never()).updateMyAgentProfile(eq(7L), any());
    }

    @Test
    void agentCanUpdateAgentProfile() throws Exception {
        when(userAccountService.updateMyAgentProfile(eq(11L), any()))
                .thenReturn(profile(Role.AGENT));

        mockMvc.perform(patch("/api/me/agent-profile")
                        .with(asPrincipal(11L, Role.AGENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licenseNumber": "LIC-2",
                                  "agency": "Lekki Realty"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("AGENT")));
    }

    private static PrivateUserProfile profile(Role role) {
        return new PrivateUserProfile(
                7L,
                "owner@example.com",
                "Ada Lovelace",
                "Ada",
                "+2348000000000",
                role,
                Instant.parse("2026-05-10T10:15:30Z"),
                role == Role.AGENT ? Instant.parse("2026-05-10T11:15:30Z") : null,
                role == Role.AGENT ? "LIC-2" : null,
                role == Role.AGENT ? "Lekki Realty" : null,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null);
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
