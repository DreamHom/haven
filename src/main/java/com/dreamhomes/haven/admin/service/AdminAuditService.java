package com.dreamhomes.haven.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AdminAuditLog;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.admin.AdminAuditApi;
import com.dreamhomes.haven.admin.AdminAuditLogRepository;

/**
 * Implementation of {@link AdminAuditApi}. The cross-feature entry point — features
 * outside admin (e.g. review takedown) call this to record an audit row.
 *
 * <p>Internal admin services (AdminListing/User/Verification) write audit rows directly
 * via {@link AdminAuditLogRepository} since they're in this module.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService implements AdminAuditApi {

    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void record(Long adminId, AdminAction action, AuditTargetType targetType,
                       Long targetId, Map<String, Object> metadata) {
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .metadata(serialize(metadata))
                .build());
    }

    /**
     * Backs {@code GET /api/admin/audit-logs}. Every filter optional; null = wildcard.
     * Persona audit (Dayo) — the read-side that made every other moderation guarantee
     * unfalsifiable until now.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.dreamhomes.haven.admin.dto.AdminAuditLogResponse> list(
            Long actorId, AdminAction action, AuditTargetType targetType, Long targetId,
            java.time.Instant from, java.time.Instant to,
            org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.search(actorId, action, targetType, targetId, from, to, pageable)
                .map(log -> new com.dreamhomes.haven.admin.dto.AdminAuditLogResponse(
                        log.getId(), log.getAdminId(), log.getAction(),
                        log.getTargetType(), log.getTargetId(),
                        log.getMetadata(), log.getCreatedAt()));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise admin audit metadata", e);
        }
    }
}
