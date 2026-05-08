package com.dreamhomes.haven.verification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Admin-facing slice of the verification feature. Replaces direct
 * {@code VerificationRepository} access from {@code feature/admin/impl} — that
 * direct impl-impl edge was the original "admin reaches into verification"
 * exception in {@code TRADEOFFS.md}.
 *
 * <p>This api owns the full decision write inside one transaction:
 * status transition + decision metadata + verified-badge stamp on the right entity
 * (delegating to {@code UserAdminApi} or {@code PropertyApi} per type). Admin-impl's
 * orchestration shrinks to writing the audit log row, sending the notification, and
 * recording metrics — all of which are admin's cross-cutting concerns.</p>
 */
public interface VerificationAdminApi {

    /**
     * Page of pending verifications of a given type, ordered by submission time.
     */
    Page<VerificationAdminView> listPending(VerificationType type, Pageable pageable);

    /**
     * Approve a pending verification. Flips status to APPROVED, stamps the decision
     * metadata, and triggers the appropriate badge stamp:
     * <ul>
     *   <li>{@code OWNER_IDENTITY} / {@code APPLICANT_IDENTITY} → {@code UserAdminApi#markIdentityVerified}</li>
     *   <li>{@code AGENT_CREDENTIALS} → {@code UserAdminApi#markAgentCredentialVerified}</li>
     *   <li>{@code PROPERTY_DOCUMENTS} → {@code PropertyApi#markDocumentsVerified}</li>
     * </ul>
     *
     * @throws VerificationNotFoundException        if the id doesn't exist
     * @throws VerificationAlreadyDecidedException  if the row isn't PENDING
     */
    VerificationAdminView approve(Long adminId, Long verificationId, String reason);

    /**
     * Reject a pending verification. Flips status to REJECTED with the reason — no
     * badge stamps fire. Reason is required.
     *
     * @throws VerificationNotFoundException        if the id doesn't exist
     * @throws VerificationAlreadyDecidedException  if the row isn't PENDING
     * @throws IllegalArgumentException             if the reason is blank
     */
    VerificationAdminView reject(Long adminId, Long verificationId, String reason);
}
