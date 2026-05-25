package com.dreamhomes.haven.verification.automation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SCAFFOLDED v2 — Smile ID integration. Every method body throws
 * {@link UnsupportedOperationException}; the class exists so the swap mechanism
 * ({@code HAVEN_VERIFICATION_PROVIDER=smile-id}) is testable end-to-end before
 * the real implementation lands.
 *
 * <p>v2 work, per method:
 * <ul>
 *   <li>{@link #verifyOwnerIdentity(OwnerIdentityCheckRequest)} →
 *       {@code POST https://api.smileidentity.com/v1/id_verification} with
 *       {@code partner_id, signature, user_id, id_type: "NIN", id_number}.
 *       See <a href="https://docs.smileidentity.com/products/biometric-kyc">Smile ID Biometric KYC docs</a>.</li>
 *   <li>{@link #verifyAgentCredentials(AgentCredentialsCheckRequest)} →
 *       {@code POST https://api.smileidentity.com/v1/business_verification} with
 *       the agent's licence number; map their {@code Actions.Verify_Business_License}
 *       outcome to our status enum.</li>
 *   <li>{@link #verifyApplicantIdentity(ApplicantIdentityCheckRequest)} → same endpoint
 *       as owner identity with a different {@code id_type} per applicant doc.</li>
 *   <li>{@link #verifyPropertyDocuments(PropertyDocumentCheckRequest)} → Smile ID
 *       doesn't have first-party property-doc verification; route to a sister provider
 *       (Sourcefin lands registry) or capture the doc + flag for human review.</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * Set {@code HAVEN_VERIFICATION_PROVIDER=smile-id} to pick this bean over
 * {@link MockVerificationProvider}.
 */
@Component
@ConditionalOnProperty(name = "haven.verification.provider", havingValue = "smile-id")
public class SmileIdVerificationProvider implements VerificationProvider {

    public static final String PROVIDER_NAME = "SMILE_ID";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public AutomatedCheckResult verifyOwnerIdentity(OwnerIdentityCheckRequest req) {
        // TODO: v2 — integrate Smile ID
        // 1. Acquire credentials from secrets (see SecretsConfig)
        // 2. POST https://api.smileidentity.com/v1/id_verification
        //    Body: { partner_id, signature, user_id, id_type: "NIN", id_number, ... }
        // 3. Parse response into AutomatedCheckResult — see Smile ID's response schema
        // 4. Handle rate limits + retries — 429 above 60 req/min on the trial tier
        // 5. Map status codes to ours (PASSED / FAILED / NEEDS_HUMAN_REVIEW)
        throw new UnsupportedOperationException(
                "TODO: integrate Smile ID — see https://docs.smileidentity.com/products/biometric-kyc");
    }

    @Override
    public AutomatedCheckResult verifyAgentCredentials(AgentCredentialsCheckRequest req) {
        // TODO: v2 — integrate Smile ID business_verification
        // POST https://api.smileidentity.com/v1/business_verification
        // Verify the agent's real-estate licence is ACTIVE in the partner registry.
        throw new UnsupportedOperationException(
                "TODO: integrate Smile ID — see https://docs.smileidentity.com/products/business-verification");
    }

    @Override
    public AutomatedCheckResult verifyApplicantIdentity(ApplicantIdentityCheckRequest req) {
        // TODO: v2 — integrate Smile ID id_verification with applicant's NIN.
        throw new UnsupportedOperationException(
                "TODO: integrate Smile ID — see https://docs.smileidentity.com/products/biometric-kyc");
    }

    @Override
    public AutomatedCheckResult verifyPropertyDocuments(PropertyDocumentCheckRequest req) {
        // TODO: v2 — Smile ID has no first-party property-doc product; either route to
        // a sister provider (Sourcefin Nigerian Lands Registry) or capture the doc and
        // mark NEEDS_HUMAN_REVIEW.
        throw new UnsupportedOperationException(
                "TODO: integrate Smile ID — see https://docs.smileidentity.com/products");
    }
}
