package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-moderation actions (PRD §4.10). Suspending bumps the user's
 * {@code tokenVersion} so every outstanding JWT is rejected by
 * {@code JwtAuthenticationFilter} on the next request — there is no admin "logout
 * everywhere else" protocol; bumping the version IS the protocol.
 *
 * <p>{@code AuthService.login} also rejects suspended users, so a re-login attempt
 * surfaces 401 instead of issuing a fresh-yet-useless token.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminMetrics adminMetrics;

    @Transactional
    public User suspend(Long adminId, Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Suspension reason is required");
        }
        if (adminId.equals(userId)) {
            throw new CannotModerateSelfException();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getSuspendedAt() != null) {
            throw new UserAlreadySuspendedException(userId);
        }
        user.setSuspendedAt(Instant.now());
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        recordAudit(adminId, AdminAction.USER_SUSPENDED, userId, reason);
        adminMetrics.recordUserModeration(AdminAction.USER_SUSPENDED);
        log.info("Admin {} suspended userId={} reason='{}'", adminId, userId, reason);
        return user;
    }

    @Transactional
    public User reactivate(Long adminId, Long userId) {
        if (adminId.equals(userId)) {
            throw new CannotModerateSelfException();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getSuspendedAt() == null) {
            throw new UserNotSuspendedException(userId);
        }
        user.setSuspendedAt(null);
        // No tokenVersion bump on reactivate — the suspend bump already invalidated tokens,
        // and the user must log in fresh. Bumping again would be wasted churn.
        userRepository.save(user);

        recordAudit(adminId, AdminAction.USER_REACTIVATED, userId, null);
        adminMetrics.recordUserModeration(AdminAction.USER_REACTIVATED);
        log.info("Admin {} reactivated userId={}", adminId, userId);
        return user;
    }

    private void recordAudit(Long adminId, AdminAction action, Long userId, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(AuditTargetType.USER)
                .targetId(userId)
                .metadata(metadata.isEmpty() ? null : serialize(metadata))
                .createdAt(Instant.now())
                .build());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise admin user payload", e);
        }
    }
}
