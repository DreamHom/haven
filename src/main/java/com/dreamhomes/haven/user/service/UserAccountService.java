package com.dreamhomes.haven.user.service;

import com.dreamhomes.haven.auth.dto.UpdateMyAgentProfileRequest;
import com.dreamhomes.haven.user.dto.PrivateUserProfile;
import com.dreamhomes.haven.user.exception.AgentLicenseAlreadyTakenException;
import com.dreamhomes.haven.user.exception.AgentProfileNotFoundException;
import com.dreamhomes.haven.user.exception.CurrentPasswordIncorrectException;
import com.dreamhomes.haven.user.exception.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.exception.NotAnAgentException;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.photo.storage.AvatarPhotoStorage;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Self-service account-management writes for {@code /api/me/*}. Every method
 * looks up the target user from the JWT-provided {@code userId} only — there is
 * no path where the caller can specify which account to mutate, which is the
 * core security contract of this surface.
 *
 * <p>Password change + email change both bump {@code tokenVersion} so every
 * previously-issued JWT for the account is revoked on its next request. This
 * defends against a leaked-token-then-pivot attack: stealing a JWT briefly is
 * not enough to keep access if the user changes either credential.</p>
 *
 * <p>Uniqueness on email + agent license is enforced both with a pre-check
 * (cheap happy path) and a {@link DataIntegrityViolationException} catch on
 * the save (race-safe). The catch is what makes the 409 contract correct under
 * concurrent registers; the pre-check just avoids a DB roundtrip for the common
 * case.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvatarPhotoStorage avatarPhotoStorage;

    @Transactional(readOnly = true)
    public PrivateUserProfile findMyProfile(Long userId) {
        User user = requireUser(userId);
        return toPrivateUserProfile(user, loadAgentProfileIfPresent(user));
    }

    @Transactional
    public PrivateUserProfile uploadMyAvatar(Long userId, MultipartFile file) {
        User user = requireUser(userId);
        String url = avatarPhotoStorage.upload(file, userId);
        user.setProfileImageUrl(url);
        userRepository.save(user);
        log.info("Updated profileImageUrl for userId={} from avatar upload", userId);
        return toPrivateUserProfile(user, loadAgentProfileIfPresent(user));
    }

    @Transactional
    public void softDeleteAccount(Long userId) {
        User user = requireUser(userId);
        if (user.getAccountDeletedAt() != null) {
            return;
        }
        user.setAccountDeletedAt(java.time.Instant.now());
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        user.setEmail("deleted-" + user.getId() + "-" + java.util.UUID.randomUUID() + "@haven.invalid");
        userRepository.save(user);
        log.info("Soft-deleted userId={}", userId);
    }

    @Transactional
    public PrivateUserProfile updateMyProfile(Long userId,
                                            String email,
                                            String fullName,
                                            String displayName,
                                            String phone,
                                            String publicBio,
                                            String profileImageUrl,
                                            String notificationPreferences) {
        User user = requireUser(userId);

        // Track whether email actually changes so we know to revoke sessions.
        // Changing the address of record is enough leverage for an attacker to
        // initiate a password reset and lock the legitimate user out — so we
        // treat it as security-relevant and bump tokenVersion just like a
        // password change does.
        boolean emailChanged = false;
        if (email != null) {
            String normalized = normalize(email);
            if (!normalized.equals(user.getEmail())) {
                if (userRepository.existsByEmailAndAccountDeletedAtIsNull(normalized)) {
                    throw new EmailAlreadyTakenException();
                }
                user.setEmail(normalized);
                emailChanged = true;
            }
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
        if (publicBio != null) {
            user.setPublicBio(trimToNull(publicBio));
        }
        if (profileImageUrl != null) {
            user.setProfileImageUrl(trimToNull(profileImageUrl));
        }
        if (notificationPreferences != null) {
            String trimmed = notificationPreferences.trim();
            user.setNotificationPreferences(trimmed.isEmpty() ? "{}" : trimmed);
        }
        if (emailChanged) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException collision) {
            // TOCTOU: a concurrent register won the unique-email index race
            // between our existsByEmail check and our save. The unique
            // constraint is the actual source of truth — translate to the
            // documented 409 instead of leaking a 500.
            throw new EmailAlreadyTakenException();
        }
        if (emailChanged) {
            log.info("Updated email for userId={}; tokenVersion bumped to {} (other sessions revoked)",
                    userId, user.getTokenVersion());
        } else {
            log.info("Updated account basics for userId={}", userId);
        }
        return toPrivateUserProfile(user, loadAgentProfileIfPresent(user));
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
    public PrivateUserProfile updateMyAgentProfile(Long userId, UpdateMyAgentProfileRequest request) {
        User user = requireUser(userId);
        if (user.getRole() != Role.AGENT) {
            throw new NotAnAgentException();
        }

        AgentProfile agentProfile = agentProfileRepository.findById(userId)
                .orElseThrow(() -> new AgentProfileNotFoundException(userId));

        if (request.licenseNumber() != null) {
            String trimmedLicense = request.licenseNumber().trim();
            // Only reset the verification badge when the license value
            // actually changes — patching the same number back in (e.g. the
            // frontend re-sending all fields on every save) should not clear
            // an already-stamped credentialVerifiedAt.
            if (!trimmedLicense.equals(agentProfile.getLicenseNumber())) {
                agentProfileRepository.findByLicenseNumber(trimmedLicense)
                        .filter(existing -> !existing.getUserId().equals(userId))
                        .ifPresent(existing -> {
                            throw new AgentLicenseAlreadyTakenException();
                        });
                agentProfile.setLicenseNumber(trimmedLicense);
                agentProfile.setCredentialVerifiedAt(null);
            }
        }
        if (request.agency() != null) {
            agentProfile.setAgency(trimToNull(request.agency()));
        }
        // Discovery fields: null = no change, present (even empty) = replace.
        // This lets the FE send `[]` to clear a tag set without a special verb.
        if (request.serviceAreas() != null) {
            agentProfile.setServiceAreas(new ArrayList<>(request.serviceAreas()));
        }
        if (request.languages() != null) {
            agentProfile.setLanguages(new ArrayList<>(request.languages()));
        }
        if (request.specializationTags() != null) {
            agentProfile.setSpecializationTags(new ArrayList<>(request.specializationTags()));
        }
        if (request.feeSchedule() != null) {
            // Mirror the agency normalisation: blank-after-trim collapses to NULL on
            // the column so the wire shape is consistent.
            agentProfile.setFeeSchedule(trimToNull(request.feeSchedule()));
        }

        try {
            agentProfileRepository.save(agentProfile);
        } catch (DataIntegrityViolationException collision) {
            // TOCTOU: the unique index on license_number is the source of truth;
            // a concurrent renewal could land between our findByLicenseNumber
            // check and our save.
            throw new AgentLicenseAlreadyTakenException();
        }
        log.info("Updated agent profile for userId={}", userId);
        return toPrivateUserProfile(user, agentProfile);
    }

    private User requireUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getAccountDeletedAt() != null) {
            throw new UserNotFoundException(userId);
        }
        return user;
    }

    private AgentProfile loadAgentProfileIfPresent(User user) {
        if (user.getRole() != Role.AGENT) {
            return null;
        }
        return agentProfileRepository.findById(user.getId())
                .orElseThrow(() -> new AgentProfileNotFoundException(user.getId()));
    }

    private PrivateUserProfile toPrivateUserProfile(User user, AgentProfile agentProfile) {
        return new PrivateUserProfile(
                user.getId(),  // → PrivateUserProfile.userId — keep the /me family on `userId` (Dayo audit)
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
                user.getCreatedAt(),
                safeList(agentProfile == null ? null : agentProfile.getServiceAreas()),
                safeList(agentProfile == null ? null : agentProfile.getLanguages()),
                safeList(agentProfile == null ? null : agentProfile.getSpecializationTags()),
                agentProfile == null ? null : agentProfile.getFeeSchedule(),
                user.getPublicBio(),
                user.getProfileImageUrl(),
                user.getNotificationPreferences());
    }

    private static List<String> safeList(List<String> raw) {
        // Stable empty-list contract on the wire (mirrors UserProfileService.safeList).
        return raw == null ? Collections.emptyList() : raw;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
