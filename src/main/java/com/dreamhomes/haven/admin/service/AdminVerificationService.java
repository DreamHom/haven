package com.dreamhomes.haven.admin.service;

import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.verification.service.VerificationAdminService;
import com.dreamhomes.haven.verification.dto.VerificationAdminView;
import com.dreamhomes.haven.verification.model.VerificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AdminAuditLog;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.admin.AdminMetrics;

/**
 * Decision orchestration for the verification system (PRD §4.8). The actual data
 * write (status flip + decision metadata + verified-badge stamp) is owned by
 * {@link VerificationAdminService} inside {@code feature/verification/impl}; this service
 * is the cross-cutting half — audit log, sync notification, metrics. Admin no longer
 * compiles against verification-impl or user-impl.
 *
 * <p>Per PRD §7, listing approvals and verification updates are sync DB notifications,
 * not Kafka — so the whole flow stays in one transaction with no outbox involvement.
 * The third design diagram ({@code 03c-listing-approved.drawio}) is superseded for
 * capstone scope; see {@code haven/docs/TRADEOFFS.md}.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminVerificationService {

    private final VerificationAdminService verificationAdminService;
    private final NotificationApi notificationApi;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminMetrics adminMetrics;

    @Transactional(readOnly = true)
    public Page<VerificationAdminView> listPending(VerificationType type, Pageable pageable) {
        return verificationAdminService.listPending(type, pageable);
    }

    /**
     * Unified read — both {@code type} and {@code status} are optional. Persona audit
     * (Dayo) flagged that requiring {@code ?type=} forced a four-call fan-out to
     * assemble the morning queue.
     */
    @Transactional(readOnly = true)
    public Page<VerificationAdminView> list(VerificationType type,
                                            com.dreamhomes.haven.verification.model.VerificationStatus status,
                                            Pageable pageable) {
        return verificationAdminService.list(type, status, pageable);
    }

    @Transactional
    public VerificationAdminView approve(Long adminId, Long verificationId, String reason) {
        VerificationAdminView decided = verificationAdminService.approve(adminId, verificationId, reason);
        recordAudit(adminId, AdminAction.VERIFICATION_APPROVED, decided, reason);
        recordNotification(decided.submitterUserId(), NotificationKind.VERIFICATION_APPROVED, decided, reason);
        adminMetrics.recordVerificationDecision(decided.type(), true);
        log.info("Admin {} approved verificationId={} type={}",
                adminId, decided.id(), decided.type());
        return decided;
    }

    @Transactional
    public VerificationAdminView reject(Long adminId, Long verificationId, String reason) {
        VerificationAdminView decided = verificationAdminService.reject(adminId, verificationId, reason);
        recordAudit(adminId, AdminAction.VERIFICATION_REJECTED, decided, reason);
        recordNotification(decided.submitterUserId(), NotificationKind.VERIFICATION_REJECTED, decided, reason);
        adminMetrics.recordVerificationDecision(decided.type(), false);
        log.info("Admin {} rejected verificationId={} type={} reason='{}'",
                adminId, decided.id(), decided.type(), reason);
        return decided;
    }

    private void recordAudit(Long adminId, AdminAction action, VerificationAdminView v, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("verificationType", v.type().name());
        metadata.put("submitterUserId", v.submitterUserId());
        if (v.targetUserId() != null) {
            metadata.put("targetUserId", v.targetUserId());
        }
        if (v.targetPropertyId() != null) {
            metadata.put("targetPropertyId", v.targetPropertyId());
        }
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(AuditTargetType.VERIFICATION)
                .targetId(v.id())
                .metadata(serialize(metadata))
                .build());
    }

    private void recordNotification(Long recipientId, NotificationKind kind,
                                    VerificationAdminView v, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("verificationId", v.id());
        payload.put("verificationType", v.type().name());
        payload.put("status", v.status().name());
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        notificationApi.recordSync(kind, recipientId, payload);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise admin payload", e);
        }
    }
}
