package com.dreamhomes.haven.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserCredentialsService is the user-side facade that auth-impl now talks to instead of
 * reaching into UserRepository directly. Tests cover what crossing the api boundary owes:
 * <ul>
 *   <li>loadByEmail returns a {@link UserCredentials} projection — no User entity leak</li>
 *   <li>bumpTokenVersion atomically increments and returns the new version</li>
 *   <li>create rejects duplicates twice over (pre-check + DB constraint)</li>
 *   <li>create persists an AgentProfile only when the role is AGENT</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserCredentialsServiceTest {

    @Mock UserRepository userRepository;
    @Mock AgentProfileRepository agentProfileRepository;

    UserCredentialsService service;

    @BeforeEach
    void setUp() {
        service = new UserCredentialsService(userRepository, agentProfileRepository);
    }

    @Test
    void loadByEmailMapsEntityToCredentialsProjectionWithoutLeakingPii() {
        User stored = User.builder()
                .id(7L).email("ada@example.com").passwordHash("$2a$10$h")
                .role(Role.OWNER).fullName("Ada").phone("+234")
                .tokenVersion(3).createdAt(Instant.now()).build();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(stored));

        UserCredentials result = service.loadByEmail("ada@example.com").orElseThrow();

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.email()).isEqualTo("ada@example.com");
        assertThat(result.passwordHash()).isEqualTo("$2a$10$h");
        assertThat(result.role()).isEqualTo(Role.OWNER);
        assertThat(result.tokenVersion()).isEqualTo(3);
        assertThat(result.suspended()).isFalse();
    }

    @Test
    void loadByEmailFlagsSuspendedWhenSuspendedAtIsSet() {
        User stored = User.builder()
                .id(7L).email("ada@example.com").passwordHash("$2a$10$h")
                .role(Role.OWNER).fullName("Ada")
                .tokenVersion(1).createdAt(Instant.now())
                .suspendedAt(Instant.now()).build();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(stored));

        assertThat(service.loadByEmail("ada@example.com").orElseThrow().suspended()).isTrue();
    }

    @Test
    void loadByEmailReturnsEmptyForUnknownEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThat(service.loadByEmail("ghost@example.com")).isEmpty();
    }

    @Test
    void tokenVersionOfReturnsCurrentVersionWhenUserExists() {
        User stored = User.builder().id(7L).email("a@b").passwordHash("h")
                .role(Role.OWNER).fullName("A").tokenVersion(5)
                .createdAt(Instant.now()).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(stored));

        assertThat(service.tokenVersionOf(7L)).hasValue(5);
    }

    @Test
    void tokenVersionOfReturnsEmptyWhenUserNoLongerExists() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.tokenVersionOf(99L)).isEqualTo(OptionalInt.empty());
    }

    @Test
    void bumpTokenVersionIncrementsAndPersistsTheNewVersion() {
        User stored = User.builder().id(7L).email("a@b").passwordHash("h")
                .role(Role.OWNER).fullName("A").tokenVersion(2)
                .createdAt(Instant.now()).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(stored));

        OptionalInt next = service.bumpTokenVersion(7L);

        assertThat(next).hasValue(3);
        verify(userRepository).save(stored);
        assertThat(stored.getTokenVersion()).isEqualTo(3);
    }

    @Test
    void bumpTokenVersionForMissingUserIsNoOp() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.bumpTokenVersion(99L)).isEqualTo(OptionalInt.empty());
        verify(userRepository, never()).save(any());
    }

    @Test
    void existsByEmailDelegatesToRepository() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        assertThat(service.existsByEmail("dup@example.com")).isTrue();
    }

    @Test
    void createPersistsUserAndReturnsIdWithCreatedAt() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        RegisteredUser result = service.create(new NewUser(
                "ada@example.com", "$2a$10$h", Role.APPLICANT, "Ada", "+234", null));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("$2a$10$h");
        assertThat(cap.getValue().getRole()).isEqualTo(Role.APPLICANT);
        assertThat(cap.getValue().getCreatedAt()).isNotNull();
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.createdAt()).isNotNull();
        verify(agentProfileRepository, never()).save(any());
    }

    @Test
    void createWritesAgentProfileWhenRoleIsAgent() {
        when(userRepository.existsByEmail("agent@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(123L);
            return u;
        });

        service.create(new NewUser(
                "agent@example.com", "h", Role.AGENT, "An Agent", null, "LIC-1"));

        ArgumentCaptor<AgentProfile> cap = ArgumentCaptor.forClass(AgentProfile.class);
        verify(agentProfileRepository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(123L);
        assertThat(cap.getValue().getLicenseNumber()).isEqualTo("LIC-1");
    }

    @Test
    void createPreCheckRejectsDuplicateEmailWithoutSaving() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new NewUser(
                "dup@example.com", "h", Role.OWNER, "Dup", null, null)))
                .isInstanceOf(EmailAlreadyTakenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createTranslatesPostEncodeRaceIntoEmailAlreadyTakenException() {
        // existsByEmail returned false because the colliding insert hadn't landed yet;
        // the DB UNIQUE constraint now trips. We translate to the same wire-stable
        // exception so the caller can't tell pre-check failure from race failure.
        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(new NewUser(
                "race@example.com", "h", Role.OWNER, "Race", null, null)))
                .isInstanceOf(EmailAlreadyTakenException.class);

        verify(agentProfileRepository, never()).save(any());
    }
}
