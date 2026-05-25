package com.dreamhomes.haven.verification.service;

import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserProfileService;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import com.dreamhomes.haven.verification.dto.SubmitVerificationCommand;
import com.dreamhomes.haven.verification.exception.DuplicatePendingVerificationException;
import com.dreamhomes.haven.verification.exception.VerificationRoleMismatchException;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.automation.AutomatedVerificationService;
import com.dreamhomes.haven.verification.liveness.LivenessCheckService;
/**
 * Submission side of the verification system (PRD §4.8). The service enforces:
 * <ul>
 *   <li>Role/track consistency — only OWNERS submit OWNER_IDENTITY,
 *       only AGENTS submit AGENT_CREDENTIALS, etc.</li>
 *   <li>Property ownership — PROPERTY_DOCUMENTS may only be submitted by the property's
 *       owner.</li>
 *   <li>One pending row per (type, target) — duplicate submissions surface as 409
 *       rather than letting the partial unique index from V10 bubble a generic 23505.</li>
 * </ul>
 *
 * <p>Cross-aggregate reads go through {@link UserProfileService} and {@link PropertyService} only —
 * this module never imports user-impl or property-impl.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final UserProfileService userProfileService;
    private final PropertyService propertyService;
    private final ObjectMapper objectMapper;
    private final NotificationApi notificationApi;
    private final LivenessCheckService livenessCheckService;
    private final AutomatedVerificationService automatedVerificationService;

    /**
     * Returns the caller's own submissions, newest first. Backs
     * {@code GET /api/verifications/mine} — the read-side the persona audit
     * surfaced as missing for every persona that submits a verification.
     */
    @Transactional(readOnly = true)
    public Page<Verification> listMine(Long submitterUserId, Pageable pageable) {
        return verificationRepository.findBySubmitterUserIdOrderBySubmittedAtDesc(submitterUserId, pageable);
    }

    @Transactional
    public Verification submit(Long submitterUserId, SubmitVerificationCommand cmd) {
        Role submitterRole = userProfileService.roleOf(submitterUserId)
                .orElseThrow(() -> new UserNotFoundException(submitterUserId));

        // Validate + consume the optional liveness check id BEFORE persisting the
        // verification row. Bad liveness ids surface as 403/409 without leaving an
        // orphaned PENDING row behind (Item 19 in post-session-tasks.md).
        if (cmd.livenessCheckId() != null) {
            livenessCheckService.consume(submitterUserId, cmd.livenessCheckId());
        }

        return switch (cmd.type()) {
            case OWNER_IDENTITY     -> submitForUser(submitterUserId, submitterRole, Role.OWNER, cmd);
            case AGENT_CREDENTIALS  -> submitForUser(submitterUserId, submitterRole, Role.AGENT, cmd);
            case APPLICANT_IDENTITY -> submitForUser(submitterUserId, submitterRole, Role.APPLICANT, cmd);
            case PROPERTY_DOCUMENTS -> submitForProperty(submitterUserId, submitterRole, cmd);
        };
    }

    private Verification submitForUser(Long submitterId, Role submitterRole, Role requiredRole,
                                       SubmitVerificationCommand cmd) {
        if (submitterRole != requiredRole) {
            throw new VerificationRoleMismatchException(cmd.type(), submitterRole);
        }
        if (verificationRepository.existsByTypeAndTargetUserIdAndStatus(
                cmd.type(), submitterId, VerificationStatus.PENDING)) {
            throw new DuplicatePendingVerificationException(cmd.type());
        }
        return persist(cmd, submitterId, submitterId, null);
    }

    private Verification submitForProperty(Long submitterId, Role submitterRole,
                                           SubmitVerificationCommand cmd) {
        // Only owners can submit property docs, and only for properties they own.
        if (submitterRole != Role.OWNER) {
            throw new VerificationRoleMismatchException(cmd.type(), submitterRole);
        }
        if (cmd.propertyId() == null) {
            throw new IllegalArgumentException("propertyId is required for PROPERTY_DOCUMENTS");
        }
        Long propertyOwner = propertyService.ownerOf(cmd.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(cmd.propertyId()));
        if (!propertyOwner.equals(submitterId)) {
            // Same exception family as role mismatch — neither leaks property ownership.
            throw new VerificationRoleMismatchException(cmd.type(), submitterRole);
        }
        if (verificationRepository.existsByTypeAndTargetPropertyIdAndStatus(
                cmd.type(), cmd.propertyId(), VerificationStatus.PENDING)) {
            throw new DuplicatePendingVerificationException(cmd.type());
        }
        return persist(cmd, submitterId, null, cmd.propertyId());
    }

    private Verification persist(SubmitVerificationCommand cmd, Long submitterId,
                                 Long targetUserId, Long targetPropertyId) {
        Instant now = Instant.now();
        Verification saved = verificationRepository.save(Verification.builder()
                .type(cmd.type())
                .submitterUserId(submitterId)
                .targetUserId(targetUserId)
                .targetPropertyId(targetPropertyId)
                .status(VerificationStatus.PENDING)
                .documentRefs(serialize(cmd.documentRefs()))
                .submittedAt(now)
                .build());
        log.info("Submitted verificationId={} type={} submitterId={} targetUserId={} targetPropertyId={}",
                saved.getId(), saved.getType(), submitterId, targetUserId, targetPropertyId);
        // Item 20 (post-session-tasks.md): run the automated provider check before the
        // submitter even hears back. v1 mock always PASSES but the row lands so admins
        // can corroborate the documents with what the provider extracted. v1 routes
        // every submission to admin review regardless of automated score — see the
        // class-level Javadoc on AutomatedVerificationService for the auto-approve gate.
        automatedVerificationService.runChecksFor(saved);
        // Persona audit (Ngozi): every submission should land in the user's notifications
        // tray so the "did the system actually receive my docs?" question has a yes.
        notificationApi.recordSync(NotificationKind.VERIFICATION_SUBMITTED, submitterId,
                java.util.Map.of("verificationId", saved.getId(), "type", saved.getType().name()));
        return saved;
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise verification document refs", e);
        }
    }
}
