package com.dreamhomes.haven.admin.service;

import com.dreamhomes.haven.user.service.UserAdminService;
import com.dreamhomes.haven.user.dto.UserAdminView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.dreamhomes.haven.admin.exception.CannotModerateSelfException;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AdminAuditLog;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.admin.AdminMetrics;
import com.dreamhomes.haven.auth.service.AuthService;
import com.dreamhomes.haven.auth.JwtAuthenticationFilter;

/**
 * User-moderation orchestration (PRD §4.10). Audit log + metric writes live here;
 * the actual user-state mutation (suspendedAt + tokenVersion bump) is delegated to
 * {@link UserAdminService} so admin-impl no longer compiles against {@code feature/user/impl}.
 *
 * <p>Suspending bumps the user's {@code tokenVersion} (inside the api call) so every
 * outstanding JWT is rejected by {@code JwtAuthenticationFilter} on the next request —
 * there is no admin "logout everywhere else" protocol; bumping the version IS the
 * protocol.</p>
 *
 * <p>{@code AuthService.login} also rejects suspended users, so a re-login attempt
 * surfaces 401 instead of issuing a fresh-yet-useless token.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminUserService {

    private final UserAdminService userAdminService;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminMetrics adminMetrics;

    @Transactional
    public UserAdminView suspend(Long adminId, Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Suspension reason is required");
        }
        if (adminId.equals(userId)) {
            throw new CannotModerateSelfException();
        }
        UserAdminView suspended = userAdminService.suspend(userId);
        recordAudit(adminId, AdminAction.USER_SUSPENDED, userId, reason);
        adminMetrics.recordUserModeration(AdminAction.USER_SUSPENDED);
        log.info("Admin {} suspended userId={} reason='{}'", adminId, userId, reason);
        return suspended;
    }

    @Transactional
    public UserAdminView reactivate(Long adminId, Long userId) {
        if (adminId.equals(userId)) {
            throw new CannotModerateSelfException();
        }
        UserAdminView reactivated = userAdminService.reactivate(userId);
        recordAudit(adminId, AdminAction.USER_REACTIVATED, userId, null);
        adminMetrics.recordUserModeration(AdminAction.USER_REACTIVATED);
        log.info("Admin {} reactivated userId={}", adminId, userId);
        return reactivated;
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
