package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.NewUser;
import com.dreamhomes.haven.user.RegisteredUser;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserCredentialsApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auth's part of registration is small now: normalise email, pre-check duplicate,
 * encode password, hand a {@link NewUser} to {@link UserCredentialsApi}, and remap
 * the user-domain duplicate exception into auth's wire-stable variant. Persistence
 * (User row + AgentProfile row) is owned by the user feature and tested there.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock
    UserCredentialsApi userCredentialsApi;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userCredentialsApi, passwordEncoder, jwtService);
    }

    @Test
    void hashesPasswordAndDelegatesPersistenceToUserApi() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(userCredentialsApi.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-pw")).thenReturn("$2a$10$hashed");
        when(userCredentialsApi.create(any(NewUser.class))).thenReturn(new RegisteredUser(42L, now));

        UserResponse result = authService.register(new RegisterCommand(
                "ada@example.com", "plaintext-pw", "Ada Lovelace", "+2348012345678", Role.APPLICANT, null));

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userCredentialsApi).create(captor.capture());
        NewUser sent = captor.getValue();

        assertThat(sent.email()).isEqualTo("ada@example.com");
        assertThat(sent.passwordHash()).isEqualTo("$2a$10$hashed");
        assertThat(sent.passwordHash()).isNotEqualTo("plaintext-pw");
        assertThat(sent.role()).isEqualTo(Role.APPLICANT);
        assertThat(sent.fullName()).isEqualTo("Ada Lovelace");
        assertThat(sent.phone()).isEqualTo("+2348012345678");
        assertThat(sent.licenseNumber()).isNull();
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.email()).isEqualTo("ada@example.com");
        assertThat(result.fullName()).isEqualTo("Ada Lovelace");
        assertThat(result.role()).isEqualTo(Role.APPLICANT);
        assertThat(result.createdAt()).isEqualTo(now);
    }

    @Test
    void rejectsDuplicateEmailWithoutEncodingPassword() {
        when(userCredentialsApi.existsByEmail("dup@example.com")).thenReturn(true);

        RegisterCommand cmd = new RegisterCommand(
                "dup@example.com", "pw", "Dup User", null, Role.APPLICANT, null);

        assertThatThrownBy(() -> authService.register(cmd))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userCredentialsApi, never()).create(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void translatesUserDomainDuplicateExceptionIntoAuthWireException() {
        // TOCTOU: existsByEmail returns false because the colliding insert hasn't landed
        // yet; UserCredentialsApi.create then loses the race and the user feature throws
        // its own exception. Auth re-throws as the wire-stable variant so the controller
        // surfaces a consistent 409.
        when(userCredentialsApi.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsApi.create(any())).thenThrow(new EmailAlreadyTakenException());

        RegisterCommand cmd = new RegisterCommand(
                "race@example.com", "secret-password", "Race", null, Role.APPLICANT, null);

        assertThatThrownBy(() -> authService.register(cmd))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void normalisesEmailToLowercaseBeforeAnyDownstreamCall() {
        when(userCredentialsApi.existsByEmail("mixed@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsApi.create(any())).thenReturn(new RegisteredUser(1L, Instant.now()));

        UserResponse result = authService.register(new RegisterCommand(
                "Mixed@Example.COM", "secret-password", "Mixed Case", null, Role.APPLICANT, null));

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userCredentialsApi).create(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("mixed@example.com");
        verify(userCredentialsApi).existsByEmail("mixed@example.com");
        assertThat(result.email()).isEqualTo("mixed@example.com");
    }

    @Test
    void forwardsLicenseNumberWhenRegisteringAgent() {
        when(userCredentialsApi.existsByEmail("agent@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsApi.create(any())).thenReturn(new RegisteredUser(123L, Instant.now()));

        authService.register(new RegisterCommand(
                "agent@example.com", "secret-password", "An Agent", null, Role.AGENT, "LIC-12345"));

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userCredentialsApi).create(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(Role.AGENT);
        assertThat(captor.getValue().licenseNumber()).isEqualTo("LIC-12345");
    }

    @Test
    void allowsAdminRoleForNowButShouldBeBlockedAtControllerLayer() {
        // Service-level register accepts any role; the rule "admin is seeded only,
        // no self-registration" is enforced at the controller (DTO validation).
        // This test documents the boundary so a future change at either layer
        // doesn't silently re-open admin self-registration.
        when(userCredentialsApi.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userCredentialsApi.create(any())).thenReturn(new RegisteredUser(7L, Instant.now()));

        UserResponse result = authService.register(new RegisterCommand(
                "admin@example.com", "pw", "Admin", null, Role.ADMIN, null));
        assertThat(result.role()).isEqualTo(Role.ADMIN);
    }
}
