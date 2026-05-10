package com.dreamhomes.haven.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAdminService owns the user-state mutations admin used to perform directly when
 * admin-impl had a compile dependency on user-impl. The exception was retired by routing
 * those writes through {@link UserAdminService}; this is its impl side.
 *
 * <p>Tests cover what admin-impl can't see anymore: the suspendedAt + tokenVersion bump
 * pair, the no-bump-on-reactivate decision, and the badge-stamp paths that
 * {@code VerificationAdminService} delegates here.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock AgentProfileRepository agentProfileRepository;

    UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, agentProfileRepository);
    }

    @Test
    void suspendingStampsSuspendedAtAndBumpsTokenVersionToInvalidateOutstandingTokens() {
        User user = active(50L, Role.OWNER);
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        UserAdminView result = service.suspend(50L);

        assertThat(user.getSuspendedAt()).isNotNull();
        // Bumping tokenVersion is what makes outstanding JWTs stale on the next request.
        assertThat(user.getTokenVersion()).isEqualTo(2);
        verify(userRepository).save(user);
        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.suspendedAt()).isNotNull();
    }

    @Test
    void cannotSuspendAlreadySuspendedUser() {
        User user = active(50L, Role.OWNER);
        user.setSuspendedAt(Instant.now());
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.suspend(50L))
                .isInstanceOf(UserAlreadySuspendedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void suspendingNonExistentUserThrows404() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.suspend(404L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void reactivatingClearsSuspendedAtAndDoesNotBumpTokenVersion() {
        User user = active(50L, Role.OWNER);
        user.setSuspendedAt(Instant.now());
        user.setTokenVersion(7);  // assume a previous suspend already bumped this
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        UserAdminView result = service.reactivate(50L);

        assertThat(user.getSuspendedAt()).isNull();
        // Re-bumping would be wasted churn — the suspend bump already invalidated everything.
        assertThat(user.getTokenVersion()).isEqualTo(7);
        verify(userRepository).save(user);
        assertThat(result.suspendedAt()).isNull();
    }

    @Test
    void cannotReactivateUserThatIsNotSuspended() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(active(50L, Role.OWNER)));

        assertThatThrownBy(() -> service.reactivate(50L))
                .isInstanceOf(UserNotSuspendedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void markIdentityVerifiedStampsTimestampOnUserRow() {
        User user = active(50L, Role.OWNER);
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));
        Instant when = Instant.parse("2026-01-02T00:00:00Z");

        service.markIdentityVerified(50L, when);

        assertThat(user.getIdentityVerifiedAt()).isEqualTo(when);
        verify(userRepository).save(user);
    }

    @Test
    void markAgentCredentialVerifiedStampsTimestampOnAgentProfile() {
        AgentProfile profile = AgentProfile.builder()
                .userId(50L).licenseNumber("LIC").createdAt(Instant.now()).build();
        when(agentProfileRepository.findById(50L)).thenReturn(Optional.of(profile));
        Instant when = Instant.parse("2026-01-02T00:00:00Z");

        service.markAgentCredentialVerified(50L, when);

        assertThat(profile.getCredentialVerifiedAt()).isEqualTo(when);
        verify(agentProfileRepository).save(profile);
        verify(userRepository, never()).save(any());
    }

    @Test
    void markAgentCredentialVerifiedThrowsWhenAgentProfileMissing() {
        when(agentProfileRepository.findById(50L)).thenReturn(Optional.empty());
        // Defence-in-depth: AuthService.register creates the AgentProfile atomically
        // with the User, so this branch shouldn't fire in practice. If it does, we want
        // a loud failure so the missing row gets fixed rather than silently ignoring it.
        assertThatThrownBy(() -> service.markAgentCredentialVerified(50L, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findForAdminReturnsViewWithoutPii() {
        User user = active(50L, Role.OWNER);
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        UserAdminView view = service.findForAdmin(50L);

        assertThat(view.id()).isEqualTo(50L);
        assertThat(view.email()).isEqualTo(user.getEmail());
        assertThat(view.role()).isEqualTo(Role.OWNER);
        // No password hash, phone, fullName, or tokenVersion in the admin projection.
    }

    private static User active(Long id, Role role) {
        return User.builder().id(id).email("u@x").passwordHash("x").fullName("U")
                .role(role).tokenVersion(1).createdAt(Instant.now()).build();
    }
}
