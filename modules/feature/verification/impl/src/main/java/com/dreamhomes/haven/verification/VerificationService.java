package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.property.PropertyApi;
import com.dreamhomes.haven.property.PropertyNotFoundException;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserApi;
import com.dreamhomes.haven.user.UserNotFoundException;
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
 * <p>Cross-aggregate reads go through {@link UserApi} and {@link PropertyApi} only —
 * this module never imports user-impl or property-impl.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final UserApi userApi;
    private final PropertyApi propertyApi;
    private final ObjectMapper objectMapper;

    @Transactional
    public Verification submit(Long submitterUserId, SubmitVerificationCommand cmd) {
        Role submitterRole = userApi.roleOf(submitterUserId)
                .orElseThrow(() -> new UserNotFoundException(submitterUserId));

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
        Long propertyOwner = propertyApi.ownerOf(cmd.propertyId())
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
