package com.dreamhomes.haven.auth;

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

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
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
                "ada@example.com", "plaintext-pw", "Ada Lovelace", "+2348012345678", Role.APPLICANT);

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
                "dup@example.com", "pw", "Dup User", null, Role.APPLICANT);

        assertThatThrownBy(() -> authService.register(cmd))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("dup@example.com");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
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
                "admin@example.com", "pw", "Admin", null, Role.ADMIN);

        User result = authService.register(cmd);
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
    }
}
