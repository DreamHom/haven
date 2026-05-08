package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.property.PropertyApi;
import com.dreamhomes.haven.user.AgentProfile;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.dreamhomes.haven.verification.Verification;
import com.dreamhomes.haven.verification.VerificationAlreadyDecidedException;
import com.dreamhomes.haven.verification.VerificationNotFoundException;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.VerificationStatus;
import com.dreamhomes.haven.verification.VerificationType;
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

/**
 * Decision side of the verification system (PRD §4.8). Reads the queue, lands the
 * decision, flips the appropriate verified-badge timestamp, writes the audit log row,
 * and records a sync notification for the submitter.
 *
 * <p>Per PRD §7, listing approvals and verification updates are <strong>sync DB
 * notifications</strong>, not Kafka — so this whole flow stays in one transaction with
 * no outbox involvement. The third design diagram ({@code 03c-listing-approved.drawio})
 * is superseded for capstone scope; see {@code haven/docs/TRADEOFFS.md}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminVerificationService {

    private final VerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final PropertyApi propertyApi;
    private final NotificationApi notificationApi;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminMetrics adminMetrics;

    @Transactional(readOnly = true)
    public Page<Verification> listPending(VerificationType type, Pageable pageable) {
        return verificationRepository.findByTypeAndStatusOrderBySubmittedAtAsc(
                type, VerificationStatus.PENDING, pageable);
    }

    @Transactional
    public Verification approve(Long adminId, Long verificationId, String reason) {
        Verification verification = loadPending(verificationId);
        Instant now = Instant.now();
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setDecidedAt(now);
        verification.setDecidedByAdminId(adminId);
        verification.setDecisionReason(reason);
        // save() on a managed entity returns the same instance — keep working on the
        // local reference so the unit test's mocked repo doesn't have to stub the return.
        verificationRepository.save(verification);

        flipBadge(verification, now);
        recordAudit(adminId, AdminAction.VERIFICATION_APPROVED, verification, reason);
        recordNotification(verification.getSubmitterUserId(),
                NotificationKind.VERIFICATION_APPROVED, verification, reason);
        adminMetrics.recordVerificationDecision(verification.getType(), true);

        log.info("Admin {} approved verificationId={} type={}",
                adminId, verification.getId(), verification.getType());
        return verification;
    }

    @Transactional
    public Verification reject(Long adminId, Long verificationId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        Verification verification = loadPending(verificationId);
        Instant now = Instant.now();
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setDecidedAt(now);
        verification.setDecidedByAdminId(adminId);
        verification.setDecisionReason(reason);
        verificationRepository.save(verification);

        recordAudit(adminId, AdminAction.VERIFICATION_REJECTED, verification, reason);
        recordNotification(verification.getSubmitterUserId(),
                NotificationKind.VERIFICATION_REJECTED, verification, reason);
        adminMetrics.recordVerificationDecision(verification.getType(), false);

        log.info("Admin {} rejected verificationId={} type={} reason='{}'",
                adminId, verification.getId(), verification.getType(), reason);
        return verification;
    }

    private Verification loadPending(Long id) {
        Verification verification = verificationRepository.findById(id)
                .orElseThrow(() -> new VerificationNotFoundException(id));
        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new VerificationAlreadyDecidedException(id, verification.getStatus());
        }
        return verification;
    }

    /**
     * Stamps the appropriate verified-badge timestamp on the right entity. Each track
     * lands on a different table; the switch keeps the per-track surgery isolated.
     */
    private void flipBadge(Verification approved, Instant when) {
        switch (approved.getType()) {
            case OWNER_IDENTITY, APPLICANT_IDENTITY -> {
                User user = userRepository.findById(approved.getTargetUserId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Target user " + approved.getTargetUserId() + " missing on approved verification"));
                user.setIdentityVerifiedAt(when);
                userRepository.save(user);
            }
            case AGENT_CREDENTIALS -> {
                AgentProfile profile = agentProfileRepository.findById(approved.getTargetUserId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Agent profile for user " + approved.getTargetUserId() + " missing"));
                profile.setCredentialVerifiedAt(when);
                agentProfileRepository.save(profile);
            }
            case PROPERTY_DOCUMENTS -> propertyApi.markDocumentsVerified(
                    approved.getTargetPropertyId(), when);
        }
    }

    private void recordAudit(Long adminId, AdminAction action, Verification verification, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("verificationType", verification.getType().name());
        metadata.put("submitterUserId", verification.getSubmitterUserId());
        if (verification.getTargetUserId() != null) {
            metadata.put("targetUserId", verification.getTargetUserId());
        }
        if (verification.getTargetPropertyId() != null) {
            metadata.put("targetPropertyId", verification.getTargetPropertyId());
        }
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(AuditTargetType.VERIFICATION)
                .targetId(verification.getId())
                .metadata(serialize(metadata))
                .createdAt(Instant.now())
                .build());
    }

    private void recordNotification(Long recipientId, NotificationKind kind,
                                    Verification verification, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("verificationId", verification.getId());
        payload.put("verificationType", verification.getType().name());
        payload.put("status", verification.getStatus().name());
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
