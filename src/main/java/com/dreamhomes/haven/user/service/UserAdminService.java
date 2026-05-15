package com.dreamhomes.haven.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import com.dreamhomes.haven.user.dto.UserAdminView;
import com.dreamhomes.haven.user.exception.UserAlreadySuspendedException;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.dreamhomes.haven.user.exception.UserNotSuspendedException;
import com.dreamhomes.haven.user.mapping.UserAdminMapper;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
/**
 * Owns the admin-driven moderation writes for the user feature. Replaces direct
 * {@code UserRepository} + {@code AgentProfileRepository} writes from
 * {@code feature/admin/impl}.
 *
 * <p>Admin-impl no longer sees {@link User} or any user-repo — it calls
 * {@link UserAdminService} and assembles its own wire response from {@link UserAdminView}.
 * Audit log + notification + metric writes stay on the admin side; this service does
 * only the user-state mutation.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final UserAdminMapper userAdminMapper;

    @Transactional
    public UserAdminView suspend(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getSuspendedAt() != null) {
            throw new UserAlreadySuspendedException(userId);
        }
        user.setSuspendedAt(Instant.now());
        // Bumping tokenVersion is the "log them out everywhere" protocol — every JWT
        // already issued for this user is now stale and rejected by the auth filter.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        log.info("Suspended userId={} (tokenVersion bumped to {})", userId, user.getTokenVersion());
        return userAdminMapper.toView(user);
    }

    @Transactional
    public UserAdminView reactivate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getSuspendedAt() == null) {
            throw new UserNotSuspendedException(userId);
        }
        user.setSuspendedAt(null);
        // No tokenVersion bump on reactivate — the suspend bump already invalidated
        // outstanding JWTs, and the user has to log in fresh anyway.
        userRepository.save(user);
        log.info("Reactivated userId={}", userId);
        return userAdminMapper.toView(user);
    }

    @Transactional
    public void markIdentityVerified(Long userId, Instant when) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setIdentityVerifiedAt(when);
        userRepository.save(user);
    }

    @Transactional
    public void markAgentCredentialVerified(Long userId, Instant when) {
        AgentProfile profile = agentProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Agent profile for user " + userId + " missing on credential approval"));
        profile.setCredentialVerifiedAt(when);
        agentProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public UserAdminView findForAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(userAdminMapper::toView)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Admin user search. Email is a case-insensitive substring match. {@code suspended}
     * is tri-state (null = all, true = only suspended, false = only active). Persona
     * audit (Dayo) — tickets arrive with emails.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserAdminView> adminSearch(
            String email,
            Boolean suspended,
            com.dreamhomes.haven.user.model.Role role,
            org.springframework.data.domain.Pageable pageable) {
        String emailLikePattern = null;
        if (email != null && !email.isBlank()) {
            String escaped = escapeLikePattern(email.strip());
            emailLikePattern = "%" + escaped.toLowerCase(Locale.ROOT) + "%";
        }
        return userRepository.adminSearch(role, suspended, emailLikePattern, pageable)
                .map(userAdminMapper::toView);
    }

    private static String escapeLikePattern(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
