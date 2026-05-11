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
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;
import com.dreamhomes.haven.verification.VerificationRepository;

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

    @Transactional(readOnly = true)
    public Page<VerificationAdminView> listPending(VerificationType type, Pageable pageable) {
        return verificationRepository
                .findByTypeAndStatusOrderBySubmittedAtAsc(type, VerificationStatus.PENDING, pageable)
                .map(VerificationAdminService::toView);
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
        return toView(verification);
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
        return toView(verification);
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

    static VerificationAdminView toView(Verification v) {
        return new VerificationAdminView(
                v.getId(), v.getType(), v.getStatus(),
                v.getSubmitterUserId(), v.getTargetUserId(), v.getTargetPropertyId(),
                v.getDocumentRefs(), v.getSubmittedAt(),
                v.getDecidedAt(), v.getDecidedByAdminId(), v.getDecisionReason());
    }
}
