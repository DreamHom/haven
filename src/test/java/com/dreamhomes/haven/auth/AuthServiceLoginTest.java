package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    AuthService authService;

    User existingUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
        existingUser = User.builder()
                .id(7L)
                .email("ada@example.com")
                .passwordHash("$2a$10$hashed")
                .role(Role.OWNER)
                .fullName("Ada Lovelace")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void issuesJwtWhenCredentialsMatch() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("plaintext-pw", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.issue(7L, "ada@example.com", Role.OWNER)).thenReturn("the-jwt-token");

        String token = authService.login(new LoginCommand("ada@example.com", "plaintext-pw"));

        assertThat(token).isEqualTo("the-jwt-token");
    }

    @Test
    void rejectsWrongPasswordWithoutLeakingWhetherUserExists() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("ada@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issue(any(), any(), any());
    }

    @Test
    void rejectsUnknownEmailWithSameExceptionAsBadPassword() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("ghost@example.com", "any")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).issue(any(), any(), any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
