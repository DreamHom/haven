package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.AgentProfile;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    AgentProfileRepository agentProfileRepository;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, agentProfileRepository);
    }

    @Test
    void hashesPasswordAndPersistsUser() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-pw")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        RegisterCommand cmd = new RegisterCommand(
                "ada@example.com", "plaintext-pw", "Ada Lovelace", "+2348012345678", Role.APPLICANT, null);

        User result = authService.register(cmd);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();

        assertThat(persisted.getEmail()).isEqualTo("ada@example.com");
        assertThat(persisted.getPasswordHash()).isEqualTo("$2a$10$hashed");
        assertThat(persisted.getPasswordHash()).isNotEqualTo("plaintext-pw");
        assertThat(persisted.getRole()).isEqualTo(Role.APPLICANT);
        assertThat(persisted.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(persisted.getPhone()).isEqualTo("+2348012345678");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(result.getId()).isEqualTo(42L);
    }

    @Test
    void rejectsDuplicateEmailWithoutCallingSave() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        RegisterCommand cmd = new RegisterCommand(
                "dup@example.com", "pw", "Dup User", null, Role.APPLICANT, null);

        assertThatThrownBy(() -> authService.register(cmd))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void translatesConcurrentInsertRaceIntoEmailAlreadyRegisteredException() {
        // TOCTOU: existsByEmail returns false because the colliding insert hasn't landed
        // yet; save then loses the race and the DB UNIQUE constraint trips. Without
        // translation, the caller would see a 500. We translate to 409.
        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        RegisterCommand cmd = new RegisterCommand(
                "race@example.com", "secret-password", "Race", null, Role.APPLICANT, null);

        assertThatThrownBy(() -> authService.register(cmd))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void normalizesEmailToLowercaseBeforePersistingAndCheckingForDuplicates() {
        when(userRepository.existsByEmail("mixed@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterCommand cmd = new RegisterCommand(
                "Mixed@Example.COM", "secret-password", "Mixed Case", null, Role.APPLICANT, null);

        User result = authService.register(cmd);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("mixed@example.com");
        verify(userRepository).existsByEmail("mixed@example.com");
        assertThat(result.getEmail()).isEqualTo("mixed@example.com");
    }

    @Test
    void persistsAgentProfileWhenRegisteringAgent() {
        when(userRepository.existsByEmail("agent@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(123L);
            return u;
        });

        RegisterCommand cmd = new RegisterCommand(
                "agent@example.com", "secret-password", "An Agent", null, Role.AGENT, "LIC-12345");

        authService.register(cmd);

        ArgumentCaptor<AgentProfile> captor = ArgumentCaptor.forClass(AgentProfile.class);
        verify(agentProfileRepository).save(captor.capture());
        AgentProfile saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(123L);
        assertThat(saved.getLicenseNumber()).isEqualTo("LIC-12345");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void doesNotPersistAgentProfileForNonAgentRoles() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterCommand(
                "owner@example.com", "secret-password", "An Owner", null, Role.OWNER, null));

        verify(agentProfileRepository, never()).save(any());
    }

    @Test
    void allowsAdminRoleForNowButShouldBeBlockedAtControllerLayer() {
        // Service-level register accepts any role; the rule "admin is seeded only,
        // no self-registration" is enforced at the controller (DTO validation).
        // This test documents the boundary so a future change at either layer
        // doesn't silently re-open admin self-registration.
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterCommand cmd = new RegisterCommand(
                "admin@example.com", "pw", "Admin", null, Role.ADMIN, null);

        User result = authService.register(cmd);
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
    }
}
