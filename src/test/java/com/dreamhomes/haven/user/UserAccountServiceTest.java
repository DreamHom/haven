package com.dreamhomes.haven.user;

import com.dreamhomes.haven.user.dto.MyAccountProfile;
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

        MyAccountProfile updated = service.updateMyProfile(7L, " Ada@Example.com ", null, null, "   ");

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

        MyAccountProfile updated = service.updateMyAgentProfile(7L, "LIC-2", "New Agency");

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

        assertThatThrownBy(() -> service.updateMyAgentProfile(7L, "LIC-2", null))
                .isInstanceOf(AgentLicenseAlreadyTakenException.class);

        verify(agentProfileRepository, never()).save(any());
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
