package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.auth.dto.RegisterCommand;
import com.dreamhomes.haven.auth.service.AuthService;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.user.dto.NewUser;
import com.dreamhomes.haven.user.dto.RegisteredUser;
import com.dreamhomes.haven.user.exception.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auth's part of registration is small now: normalise email, pre-check duplicate,
 * encode password, hand a {@link NewUser} to {@link UserCredentialsService}. The
 * controller surfaces 202 in every case — duplicates are silently swallowed here
 * to keep the wire response indistinguishable from a fresh register and prevent
 * email enumeration. Persistence (User row + AgentProfile row) is owned by the
 * user feature and tested there.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock
    UserCredentialsService userCredentialsService;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    com.dreamhomes.haven.notification.NotificationApi notificationApi;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userCredentialsService, passwordEncoder, jwtService, notificationApi,
                org.mockito.Mockito.mock(com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository.class),
                org.mockito.Mockito.mock(com.dreamhomes.haven.auth.refresh.RefreshTokenService.class));
    }

    @Test
    void hashesPasswordAndDelegatesPersistenceToUserCredentialsService() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(userCredentialsService.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-pw")).thenReturn("$2a$10$hashed");
        when(userCredentialsService.create(any(NewUser.class))).thenReturn(new RegisteredUser(42L, now));

        authService.register(new RegisterCommand(
                "ada@example.com", "plaintext-pw", "Ada Lovelace", "Display Name",
                "+2348012345678", Role.APPLICANT, null));

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userCredentialsService).create(captor.capture());
        NewUser sent = captor.getValue();

        assertThat(sent.email()).isEqualTo("ada@example.com");
        assertThat(sent.passwordHash()).isEqualTo("$2a$10$hashed");
        assertThat(sent.passwordHash()).isNotEqualTo("plaintext-pw");
        assertThat(sent.role()).isEqualTo(Role.APPLICANT);
        assertThat(sent.fullName()).isEqualTo("Ada Lovelace");
        assertThat(sent.phone()).isEqualTo("+2348012345678");
        assertThat(sent.licenseNumber()).isNull();
    }

    @Test
    void duplicateEmailIsSilentlySwallowedWithoutEncodingPassword() {
        when(userCredentialsService.existsByEmail("dup@example.com")).thenReturn(true);

        RegisterCommand cmd = new RegisterCommand(
                "dup@example.com", "pw", "Dup User", "Display Name", null, Role.APPLICANT, null);

        // No throw — controller will respond 202, same as a fresh email. That's the
        // anti-enumeration contract.
        assertThatCode(() -> authService.register(cmd)).doesNotThrowAnyException();

        verify(userCredentialsService, never()).create(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void tocouCollisionIsSilentlySwallowed() {
        // existsByEmail returns false because the colliding insert hasn't landed yet;
        // UserCredentialsService.create then loses the race and the user feature throws
        // EmailAlreadyTakenException. We swallow it for the same reason as the up-front
        // duplicate check — caller can't distinguish.
        when(userCredentialsService.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsService.create(any())).thenThrow(new EmailAlreadyTakenException());

        RegisterCommand cmd = new RegisterCommand(
                "race@example.com", "secret-password", "Race", "Display Name", null, Role.APPLICANT, null);

        assertThatCode(() -> authService.register(cmd)).doesNotThrowAnyException();
    }

    @Test
    void normalisesEmailToLowercaseBeforeAnyDownstreamCall() {
        when(userCredentialsService.existsByEmail("mixed@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsService.create(any())).thenReturn(new RegisteredUser(1L, Instant.now()));

        authService.register(new RegisterCommand(
                "Mixed@Example.COM", "secret-password", "Mixed Case", "Display Name", null, Role.APPLICANT, null));

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userCredentialsService).create(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("mixed@example.com");
        verify(userCredentialsService).existsByEmail("mixed@example.com");
    }

    @Test
    void forwardsLicenseNumberWhenRegisteringAgent() {
        when(userCredentialsService.existsByEmail("agent@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsService.create(any())).thenReturn(new RegisteredUser(123L, Instant.now()));

        authService.register(new RegisterCommand(
                "agent@example.com", "secret-password", "An Agent", "Display Name", null, Role.AGENT, "LIC-12345"));

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userCredentialsService).create(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(Role.AGENT);
        assertThat(captor.getValue().licenseNumber()).isEqualTo("LIC-12345");
    }

    @Test
    void allowsAdminRoleAtServiceLayerButShouldBeBlockedAtControllerLayer() {
        // Service-level register accepts any role; the rule "admin is seeded only,
        // no self-registration" is enforced at the controller (DTO validation).
        // This test documents the boundary so a future change at either layer
        // doesn't silently re-open admin self-registration.
        when(userCredentialsService.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsService.create(any())).thenReturn(new RegisteredUser(7L, Instant.now()));

        authService.register(new RegisterCommand(
                "admin@example.com", "pw", "Admin", "Display Name", null, Role.ADMIN, null));

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userCredentialsService).create(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(Role.ADMIN);
    }
}
