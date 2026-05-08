package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyNotFoundException;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
 * <p>The decision side (approve/reject) lives in the admin package — see
 * {@code AdminVerificationService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Verification submit(Long submitterUserId, SubmitVerificationCommand cmd) {
        User submitter = userRepository.findById(submitterUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user " + submitterUserId + " not found"));

        return switch (cmd.type()) {
            case OWNER_IDENTITY     -> submitForUser(submitter, Role.OWNER, cmd);
            case AGENT_CREDENTIALS  -> submitForUser(submitter, Role.AGENT, cmd);
            case APPLICANT_IDENTITY -> submitForUser(submitter, Role.APPLICANT, cmd);
            case PROPERTY_DOCUMENTS -> submitForProperty(submitter, cmd);
        };
    }

    private Verification submitForUser(User submitter, Role requiredRole, SubmitVerificationCommand cmd) {
        if (submitter.getRole() != requiredRole) {
            throw new VerificationRoleMismatchException(cmd.type(), submitter.getRole());
        }
        if (verificationRepository.existsByTypeAndTargetUserIdAndStatus(
                cmd.type(), submitter.getId(), VerificationStatus.PENDING)) {
            throw new DuplicatePendingVerificationException(cmd.type());
        }
        return persist(cmd, submitter.getId(), submitter.getId(), null);
    }

    private Verification submitForProperty(User submitter, SubmitVerificationCommand cmd) {
        // Only owners can submit property docs, and only for properties they own.
        if (submitter.getRole() != Role.OWNER) {
            throw new VerificationRoleMismatchException(cmd.type(), submitter.getRole());
        }
        if (cmd.propertyId() == null) {
            throw new IllegalArgumentException("propertyId is required for PROPERTY_DOCUMENTS");
        }
        Property property = propertyRepository.findById(cmd.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(cmd.propertyId()));
        if (!property.getOwnerId().equals(submitter.getId())) {
            // Same exception family as role mismatch — neither leaks property ownership.
            throw new VerificationRoleMismatchException(cmd.type(), submitter.getRole());
        }
        if (verificationRepository.existsByTypeAndTargetPropertyIdAndStatus(
                cmd.type(), property.getId(), VerificationStatus.PENDING)) {
            throw new DuplicatePendingVerificationException(cmd.type());
        }
        return persist(cmd, submitter.getId(), null, property.getId());
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
