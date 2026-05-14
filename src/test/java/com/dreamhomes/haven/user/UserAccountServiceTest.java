package com.dreamhomes.haven.user;

import com.dreamhomes.haven.auth.dto.UpdateMyAgentProfileRequest;
import com.dreamhomes.haven.user.dto.PrivateUserProfile;
import com.dreamhomes.haven.user.exception.AgentLicenseAlreadyTakenException;
import com.dreamhomes.haven.user.exception.CurrentPasswordIncorrectException;
import com.dreamhomes.haven.user.exception.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.user.service.UserAccountService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    AgentProfileRepository agentProfileRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(userRepository, agentProfileRepository, passwordEncoder);
    }

    @Test
    void updateMyProfileNormalizesEmailAndClearsBlankPhone() {
        User user = ownerUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);

        PrivateUserProfile updated = service.updateMyProfile(7L, " Ada@Example.com ", null, null, "   ");

        assertThat(updated.email()).isEqualTo("ada@example.com");
        assertThat(updated.phone()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void updateMyProfileRejectsDuplicateEmail() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(ownerUser()));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.updateMyProfile(7L, "taken@example.com", null, null, null))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }

    @Test
    void changePasswordRehashesAndBumpsTokenVersion() {
        User user = ownerUser();
        user.setPasswordHash("old-hash");
        user.setTokenVersion(3);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        service.changePassword(7L, "old-password", "new-password-123");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(4);
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = ownerUser();
        user.setPasswordHash("old-hash");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(7L, "wrong-password", "new-password-123"))
                .isInstanceOf(CurrentPasswordIncorrectException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updatingAgentLicenseClearsCredentialVerification() {
        User user = agentUser();
        AgentProfile agentProfile = AgentProfile.builder()
                .userId(7L)
                .licenseNumber("LIC-1")
                .agency("Old Agency")
                .credentialVerifiedAt(Instant.parse("2026-05-01T00:00:00Z"))
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(agentProfileRepository.findById(7L)).thenReturn(Optional.of(agentProfile));
        when(agentProfileRepository.findByLicenseNumber("LIC-2")).thenReturn(Optional.empty());

        PrivateUserProfile updated = service.updateMyAgentProfile(7L,
                new UpdateMyAgentProfileRequest("LIC-2", "New Agency", null, null, null, null));

        assertThat(updated.licenseNumber()).isEqualTo("LIC-2");
        assertThat(updated.agency()).isEqualTo("New Agency");
        assertThat(updated.agentCredentialVerifiedAt()).isNull();
        verify(agentProfileRepository).save(agentProfile);
    }

    @Test
    void updatingAgentLicenseRejectsDuplicateNumberOwnedByAnotherAgent() {
        User user = agentUser();
        AgentProfile mine = AgentProfile.builder().userId(7L).licenseNumber("LIC-1").build();
        AgentProfile theirs = AgentProfile.builder().userId(99L).licenseNumber("LIC-2").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(agentProfileRepository.findById(7L)).thenReturn(Optional.of(mine));
        when(agentProfileRepository.findByLicenseNumber("LIC-2")).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.updateMyAgentProfile(7L,
                        new UpdateMyAgentProfileRequest("LIC-2", null, null, null, null, null)))
                .isInstanceOf(AgentLicenseAlreadyTakenException.class);

        verify(agentProfileRepository, never()).save(any());
    }

    @Test
    void updateMyProfileBumpsTokenVersionWhenEmailChanges() {
        // Email change is security-relevant: an attacker holding a leaked JWT could swap
        // the address of record and then trigger a password reset to lock the legitimate
        // user out. Bumping tokenVersion revokes every outstanding session on save.
        User user = ownerUser();
        user.setTokenVersion(3);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        service.updateMyProfile(7L, "new@example.com", null, null, null);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getTokenVersion()).isEqualTo(4);
    }

    @Test
    void updateMyProfileDoesNotBumpTokenVersionWhenEmailUnchanged() {
        // Non-email patches (name, phone) don't revoke sessions — they're not enough
        // leverage for an account-takeover pivot.
        User user = ownerUser();
        user.setTokenVersion(3);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.updateMyProfile(7L, null, "New Name", null, "+2348111111111");

        assertThat(user.getTokenVersion()).isEqualTo(3);
    }

    @Test
    void updateMyProfileTranslatesSaveTimeUniqueViolationTo409() {
        // TOCTOU: existsByEmail returned false, but a concurrent register won the
        // unique-index race between our pre-check and our save. The unique constraint
        // is the source of truth; the catch translates the generic 500-shaped
        // DataIntegrityViolationException to the documented 409.
        User user = ownerUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uniq"));

        assertThatThrownBy(() -> service.updateMyProfile(7L, "race@example.com", null, null, null))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }

    @Test
    void agentLicensePatchWithUnchangedValueKeepsCredentialVerifiedAt() {
        // The frontend re-sending all fields on every save should not silently revoke
        // the verification badge when nothing actually changed. Only a *different*
        // license number triggers the re-verify-required reset.
        User user = agentUser();
        AgentProfile agentProfile = AgentProfile.builder()
                .userId(7L)
                .licenseNumber("LIC-1")
                .agency("Old Agency")
                .credentialVerifiedAt(Instant.parse("2026-05-01T00:00:00Z"))
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(agentProfileRepository.findById(7L)).thenReturn(Optional.of(agentProfile));

        PrivateUserProfile updated = service.updateMyAgentProfile(7L,
                new UpdateMyAgentProfileRequest("LIC-1", "New Agency", null, null, null, null));

        assertThat(updated.licenseNumber()).isEqualTo("LIC-1");
        assertThat(updated.agency()).isEqualTo("New Agency");
        assertThat(updated.agentCredentialVerifiedAt())
                .isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
        // We must not even probe for a duplicate when the value didn't change.
        verify(agentProfileRepository, never()).findByLicenseNumber(any());
    }

    private static User ownerUser() {
        return User.builder()
                .id(7L)
                .email("owner@example.com")
                .passwordHash("hash")
                .role(Role.OWNER)
                .fullName("Owner Name")
                .displayName("Owner")
                .phone("+2348000000000")
                .tokenVersion(1)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private static User agentUser() {
        return User.builder()
                .id(7L)
                .email("agent@example.com")
                .passwordHash("hash")
                .role(Role.AGENT)
                .fullName("Agent Name")
                .displayName("Agent")
                .tokenVersion(1)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
