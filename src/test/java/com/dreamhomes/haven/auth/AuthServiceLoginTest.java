package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.dto.UserCredentials;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.auth.dto.LoginCommand;
import com.dreamhomes.haven.auth.exception.InvalidCredentialsException;
import com.dreamhomes.haven.auth.service.AuthService;
import com.dreamhomes.haven.auth.service.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    UserCredentialsService userCredentialsService;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    com.dreamhomes.haven.notification.NotificationApi notificationApi;

    AuthService authService;

    UserCredentials existing;

    @BeforeEach
    void setUp() {
        com.dreamhomes.haven.auth.refresh.RefreshTokenService refreshTokenService =
                org.mockito.Mockito.mock(com.dreamhomes.haven.auth.refresh.RefreshTokenService.class);
        // Negative-path tests (unknown email, wrong password, suspended user, timing-attack guard)
        // all throw InvalidCredentialsException BEFORE refresh-token issuance, so these stubbings
        // go unused on those paths. Wrap in lenient() to keep them available for the success-path
        // tests without tripping Mockito's strict-stubbing check on the negative ones.
        org.mockito.Mockito.lenient().when(refreshTokenService.issue(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.dreamhomes.haven.auth.refresh.IssuedRefreshToken(
                        "test-refresh-token", java.time.Instant.now().plusSeconds(2_592_000)));
        org.mockito.Mockito.lenient().when(refreshTokenService.expirationSeconds()).thenReturn(2_592_000L);
        authService = new AuthService(userCredentialsService, passwordEncoder, jwtService, notificationApi,
                org.mockito.Mockito.mock(com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository.class),
                refreshTokenService);
        existing = new UserCredentials(
                7L, "ada@example.com", "Ada Lovelace", "$2a$10$hashed", Role.OWNER, 1, false);
    }

    @Test
    void issuesJwtWhenCredentialsMatch() {
        when(userCredentialsService.loadByEmail("ada@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("plaintext-pw", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.issue(7L, "ada@example.com", Role.OWNER, 1)).thenReturn("the-jwt-token");
        when(jwtService.expirationSeconds()).thenReturn(3600L);

        com.dreamhomes.haven.auth.dto.LoginResult result =
                authService.login(new LoginCommand("ada@example.com", "plaintext-pw"));

        assertThat(result.token()).isEqualTo("the-jwt-token");
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.role()).isEqualTo(Role.OWNER);
        assertThat(result.fullName()).isEqualTo("Ada Lovelace");
        assertThat(result.expiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    void rejectsWrongPasswordWithoutLeakingWhetherUserExists() {
        when(userCredentialsService.loadByEmail("ada@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("ada@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issue(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() {
        when(userCredentialsService.loadByEmail("ada@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("plaintext-pw", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.issue(7L, "ada@example.com", Role.OWNER, 1)).thenReturn("the-jwt-token");
        when(jwtService.expirationSeconds()).thenReturn(3600L);

        com.dreamhomes.haven.auth.dto.LoginResult result =
                authService.login(new LoginCommand("ADA@Example.COM", "plaintext-pw"));

        assertThat(result.token()).isEqualTo("the-jwt-token");
        verify(userCredentialsService).loadByEmail("ada@example.com");
    }

    @Test
    void rejectsUnknownEmailWithSameExceptionAsBadPassword() {
        when(userCredentialsService.loadByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("ghost@example.com", "any")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issue(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void suspendedUserCannotLoginEvenWithCorrectPassword() {
        UserCredentials suspended = new UserCredentials(
                7L, "ada@example.com", "Ada Lovelace", "$2a$10$hashed", Role.OWNER, 1, true);
        when(userCredentialsService.loadByEmail("ada@example.com")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("plaintext-pw", "$2a$10$hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand("ada@example.com", "plaintext-pw")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issue(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void runsPasswordHashEvenWhenUserMissingToBlockTimingBasedEnumeration() {
        // If we returned early on missing user, an attacker could distinguish "user exists
        // but wrong password" from "user does not exist" by response timing. Always run
        // matches() so the wall-clock cost of a login attempt is the same either way.
        when(userCredentialsService.loadByEmail("ghost@example.com")).thenReturn(Optional.empty());

        try {
            authService.login(new LoginCommand("ghost@example.com", "irrelevant"));
        } catch (InvalidCredentialsException expected) {
            // expected
        }

        verify(passwordEncoder).matches(eq("irrelevant"), org.mockito.ArgumentMatchers.anyString());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
