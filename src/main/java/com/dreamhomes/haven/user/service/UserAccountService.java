package com.dreamhomes.haven.user.service;

import com.dreamhomes.haven.user.dto.MyAccountProfile;
import com.dreamhomes.haven.user.exception.AgentLicenseAlreadyTakenException;
import com.dreamhomes.haven.user.exception.AgentProfileNotFoundException;
import com.dreamhomes.haven.user.exception.CurrentPasswordIncorrectException;
import com.dreamhomes.haven.user.exception.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.exception.NotAnAgentException;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MyAccountProfile findMyProfile(Long userId) {
        User user = requireUser(userId);
        return toMyAccountProfile(user, loadAgentProfileIfPresent(user));
    }

    @Transactional
    public MyAccountProfile updateMyProfile(Long userId,
                                            String email,
                                            String fullName,
                                            String displayName,
                                            String phone) {
        User user = requireUser(userId);

        if (email != null) {
            String normalized = normalize(email);
            if (!normalized.equals(user.getEmail()) && userRepository.existsByEmail(normalized)) {
                throw new EmailAlreadyTakenException();
            }
            user.setEmail(normalized);
        }
        if (fullName != null) {
            user.setFullName(fullName.trim());
        }
        if (displayName != null) {
            user.setDisplayName(displayName.trim());
        }
        if (phone != null) {
            user.setPhone(trimToNull(phone));
        }

        userRepository.save(user);
        log.info("Updated account basics for userId={}", userId);
        return toMyAccountProfile(user, loadAgentProfileIfPresent(user));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CurrentPasswordIncorrectException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        log.info("Changed password for userId={} and bumped tokenVersion to {}",
                userId, user.getTokenVersion());
    }

    @Transactional
    public MyAccountProfile updateMyAgentProfile(Long userId, String licenseNumber, String agency) {
        User user = requireUser(userId);
        if (user.getRole() != Role.AGENT) {
            throw new NotAnAgentException();
        }

        AgentProfile agentProfile = agentProfileRepository.findById(userId)
                .orElseThrow(() -> new AgentProfileNotFoundException(userId));

        if (licenseNumber != null) {
            String trimmedLicense = licenseNumber.trim();
            agentProfileRepository.findByLicenseNumber(trimmedLicense)
                    .filter(existing -> !existing.getUserId().equals(userId))
                    .ifPresent(existing -> {
                        throw new AgentLicenseAlreadyTakenException();
                    });
            agentProfile.setLicenseNumber(trimmedLicense);
            agentProfile.setCredentialVerifiedAt(null);
        }
        if (agency != null) {
            agentProfile.setAgency(trimToNull(agency));
        }

        agentProfileRepository.save(agentProfile);
        log.info("Updated agent profile for userId={}", userId);
        return toMyAccountProfile(user, agentProfile);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private AgentProfile loadAgentProfileIfPresent(User user) {
        if (user.getRole() != Role.AGENT) {
            return null;
        }
        return agentProfileRepository.findById(user.getId())
                .orElseThrow(() -> new AgentProfileNotFoundException(user.getId()));
    }

    private MyAccountProfile toMyAccountProfile(User user, AgentProfile agentProfile) {
        return new MyAccountProfile(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRole(),
                user.getIdentityVerifiedAt(),
                agentProfile == null ? null : agentProfile.getCredentialVerifiedAt(),
                agentProfile == null ? null : agentProfile.getLicenseNumber(),
                agentProfile == null ? null : agentProfile.getAgency(),
                user.getSuspendedAt() != null,
                user.getCreatedAt());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
