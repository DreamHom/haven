package com.dreamhomes.haven.verification.service;

import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.user.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import com.dreamhomes.haven.verification.dto.VerificationAdminView;
import com.dreamhomes.haven.verification.exception.VerificationAlreadyDecidedException;
import com.dreamhomes.haven.verification.exception.VerificationNotFoundException;
import com.dreamhomes.haven.verification.mapping.VerificationAdminMapper;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.automation.AutomatedCheckResultResponse;
import com.dreamhomes.haven.verification.automation.VerificationAutomationResultRepository;

import java.util.List;

/**
 * Decision side of the verification system (PRD §4.8). Replaces what was previously
 * the "admin reaches into verification-impl + user-impl" exception in TRADEOFFS — the
 * admin feature now drives this through {@link VerificationAdminService} and never sees
 * the {@link Verification} entity.
 *
 * <p>Owns the full decision write inside one transaction: status transition,
 * decision metadata, and the verified-badge stamp (delegated to
 * {@link UserAdminService#markIdentityVerified} /
 * {@link UserAdminService#markAgentCredentialVerified} / {@link PropertyService#markDocumentsVerified}
 * per type). Audit log + notification + metric writes stay in admin-impl because they
 * are admin's cross-cutting concerns.
 *
 * <p>Per PRD §7, listing approvals and verification updates are sync DB notifications,
 * not Kafka — this whole flow stays in one transaction with no outbox involvement.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationAdminService {

    private final VerificationRepository verificationRepository;
    private final UserAdminService userAdminService;
    private final PropertyService propertyService;
    private final VerificationAdminMapper verificationAdminMapper;
    private final VerificationAutomationResultRepository automationResultRepository;

    @Transactional(readOnly = true)
    public Page<VerificationAdminView> listPending(VerificationType type, Pageable pageable) {
        return list(type, VerificationStatus.PENDING, pageable);
    }

    /**
     * Unified admin queue read: both {@code type} and {@code status} are optional.
     * Persona audit (Dayo) flagged that requiring {@code ?type=...} forced a
     * four-call fan-out to assemble the morning queue.
     */
    @Transactional(readOnly = true)
    public Page<VerificationAdminView> list(VerificationType type, VerificationStatus status, Pageable pageable) {
        if (type != null && status != null) {
            return verificationRepository
                    .findByTypeAndStatusOrderBySubmittedAtAsc(type, status, pageable)
                    .map(this::toViewWithAutomatedChecks);
        }
        if (type != null) {
            return verificationRepository
                    .findByTypeOrderBySubmittedAtAsc(type, pageable)
                    .map(this::toViewWithAutomatedChecks);
        }
        if (status != null) {
            return verificationRepository
                    .findByStatusOrderBySubmittedAtAsc(status, pageable)
                    .map(this::toViewWithAutomatedChecks);
        }
        return verificationRepository
                .findAllByOrderBySubmittedAtAsc(pageable)
                .map(this::toViewWithAutomatedChecks);
    }

    @Transactional
    public VerificationAdminView approve(Long adminId, Long verificationId, String reason) {
        Verification verification = loadPending(verificationId);
        Instant now = Instant.now();
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setDecidedAt(now);
        verification.setDecidedByAdminId(adminId);
        verification.setDecisionReason(reason);
        verificationRepository.save(verification);

        flipBadge(verification, now);

        log.info("Admin {} approved verificationId={} type={}",
                adminId, verification.getId(), verification.getType());
        return toViewWithAutomatedChecks(verification);
    }

    @Transactional
    public VerificationAdminView reject(Long adminId, Long verificationId, String reason) {
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

        log.info("Admin {} rejected verificationId={} type={} reason='{}'",
                adminId, verification.getId(), verification.getType(), reason);
        return toViewWithAutomatedChecks(verification);
    }

    /**
     * Composes the {@link VerificationAdminView} with the automated check rows
     * (Item 20). The mapper handles the verification-side fields; we splice in the
     * automation rows here because they live in a sibling aggregate.
     */
    private VerificationAdminView toViewWithAutomatedChecks(Verification verification) {
        VerificationAdminView base = verificationAdminMapper.toView(verification);
        List<AutomatedCheckResultResponse> automated = automationResultRepository
                .findByVerificationIdOrderByRunAtAsc(verification.getId()).stream()
                .map(AutomatedCheckResultResponse::from)
                .toList();
        return new VerificationAdminView(
                base.id(), base.type(), base.status(),
                base.submitterUserId(), base.targetUserId(), base.targetPropertyId(),
                base.documentRefs(), base.submittedAt(),
                base.decidedAt(), base.decidedByAdminId(), base.decisionReason(),
                automated.isEmpty() ? null : automated);
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
     * Stamps the appropriate verified-badge timestamp on the right entity by delegating
     * to the owning feature's api. Each type lands on a different aggregate; the switch
     * keeps the per-track surgery isolated.
     */
    private void flipBadge(Verification approved, Instant when) {
        switch (approved.getType()) {
            case OWNER_IDENTITY, APPLICANT_IDENTITY ->
                    userAdminService.markIdentityVerified(approved.getTargetUserId(), when);
            case AGENT_CREDENTIALS ->
                    userAdminService.markAgentCredentialVerified(approved.getTargetUserId(), when);
            case PROPERTY_DOCUMENTS ->
                    propertyService.markDocumentsVerified(approved.getTargetPropertyId(), when);
        }
    }

}
