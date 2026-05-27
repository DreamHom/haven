package com.dreamhomes.haven.verification.automation;

import com.dreamhomes.haven.verification.model.Verification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Glue between {@link com.dreamhomes.haven.verification.service.VerificationService} and the active
 * {@link VerificationProvider}. Dispatches per-type, persists each
 * {@link AutomatedCheckResult} as a {@link VerificationAutomationResult} row, and returns
 * the list so the calling submit service can inspect them (e.g. for an auto-approve gate
 * that may land in v2).
 *
 * <h2>Auto-approve gate — INTENTIONALLY DISABLED IN v1</h2>
 * <p>The spec includes a {@code haven.verification.auto-approve-threshold: 0.85} knob
 * for v2 to bypass admin review when every check passes confidently. For v1 we keep
 * every submission flowing through the admin queue regardless of the automated score —
 * the demo needs visible rows in the queue, and the mock provider always passes which
 * would otherwise let everything skip admin entirely. See Item 20 in
 * {@code docs/demo-prep/post-session-tasks.md} for the production gate semantics.
 *
 * <p>The threshold property is read but never used; it's a v2 placeholder so the
 * config surface is stable from day one.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AutomatedVerificationService {

    private final VerificationProvider provider;
    private final VerificationAutomationResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    /**
     * Dispatches to the provider's per-type method, persists the result, and returns the
     * list of results (one for now; spec leaves room for multiple per submission once
     * liveness folds in here in v2).
     */
    @Transactional
    public List<AutomatedCheckResult> runChecksFor(Verification verification) {
        AutomatedCheckResult result = switch (verification.getType()) {
            case OWNER_IDENTITY -> provider.verifyOwnerIdentity(
                    new OwnerIdentityCheckRequest(
                            verification.getId(),
                            verification.getSubmitterUserId(),
                            verification.getDocumentRefs()));
            case AGENT_CREDENTIALS -> provider.verifyAgentCredentials(
                    new AgentCredentialsCheckRequest(
                            verification.getId(),
                            verification.getSubmitterUserId(),
                            verification.getDocumentRefs()));
            case APPLICANT_IDENTITY -> provider.verifyApplicantIdentity(
                    new ApplicantIdentityCheckRequest(
                            verification.getId(),
                            verification.getSubmitterUserId(),
                            verification.getDocumentRefs()));
            case PROPERTY_DOCUMENTS -> provider.verifyPropertyDocuments(
                    new PropertyDocumentCheckRequest(
                            verification.getId(),
                            verification.getSubmitterUserId(),
                            verification.getTargetPropertyId(),
                            verification.getDocumentRefs()));
        };

        resultRepository.save(VerificationAutomationResult.builder()
                .verificationId(verification.getId())
                .checkType(verification.getType().name())
                .providerName(provider.name())
                .status(result.status())
                .score(result.score())
                .extractedFields(serialize(result.extractedFields()))
                .providerReference(result.providerReference())
                .rawResponse(result.rawResponse())
                .runAt(result.runAt())
                .build());

        log.info("Automated verification check verificationId={} type={} provider={} status={} score={}",
                verification.getId(), verification.getType(), provider.name(),
                result.status(), result.score());

        return List.of(result);
    }

    private String serialize(Object payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise automated check fields", e);
        }
    }
}
