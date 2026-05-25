package com.dreamhomes.haven.verification.automation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SCAFFOLDED v2 — Dojah integration (Nigerian KYC alternative to Smile ID). Every
 * method body throws {@link UnsupportedOperationException}; demonstrates that swapping
 * providers is one env var ({@code HAVEN_VERIFICATION_PROVIDER=dojah}) and no code
 * change downstream.
 *
 * <p>v2 work, per method:
 * <ul>
 *   <li>{@link #verifyOwnerIdentity(OwnerIdentityCheckRequest)} →
 *       {@code POST https://api.dojah.io/api/v1/kyc/nin/verify} with NIN.
 *       See <a href="https://docs.dojah.io/docs/lookup-nin">Dojah NIN lookup</a>.</li>
 *   <li>{@link #verifyAgentCredentials(AgentCredentialsCheckRequest)} →
 *       {@code POST https://api.dojah.io/api/v1/kyc/business} with licence ref.</li>
 *   <li>{@link #verifyApplicantIdentity(ApplicantIdentityCheckRequest)} → same NIN
 *       endpoint as owner identity.</li>
 *   <li>{@link #verifyPropertyDocuments(PropertyDocumentCheckRequest)} → Dojah doesn't
 *       expose property-doc verification yet; same fallback as Smile ID.</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * Set {@code HAVEN_VERIFICATION_PROVIDER=dojah} to pick this bean over
 * {@link MockVerificationProvider}.
 */
@Component
@ConditionalOnProperty(name = "haven.verification.provider", havingValue = "dojah")
public class DojahVerificationProvider implements VerificationProvider {

    public static final String PROVIDER_NAME = "DOJAH";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public AutomatedCheckResult verifyOwnerIdentity(OwnerIdentityCheckRequest req) {
        // TODO: v2 — integrate Dojah NIN verification
        // POST https://api.dojah.io/api/v1/kyc/nin/verify
        // Headers: AppId, Authorization (secret key)
        // Body: { nin: "<11-digit>" }
        // Map { entity.first_name, last_name } to a name-match score against the user's profile.
        throw new UnsupportedOperationException(
                "TODO: integrate Dojah — see https://docs.dojah.io/docs/lookup-nin");
    }

    @Override
    public AutomatedCheckResult verifyAgentCredentials(AgentCredentialsCheckRequest req) {
        // TODO: v2 — Dojah business / CAC verification
        throw new UnsupportedOperationException(
                "TODO: integrate Dojah — see https://docs.dojah.io/docs/business-verification");
    }

    @Override
    public AutomatedCheckResult verifyApplicantIdentity(ApplicantIdentityCheckRequest req) {
        // TODO: v2 — same Dojah NIN flow as owner identity.
        throw new UnsupportedOperationException(
                "TODO: integrate Dojah — see https://docs.dojah.io/docs/lookup-nin");
    }

    @Override
    public AutomatedCheckResult verifyPropertyDocuments(PropertyDocumentCheckRequest req) {
        // TODO: v2 — Dojah doesn't have property-doc verification; route to a sister
        // provider or mark NEEDS_HUMAN_REVIEW.
        throw new UnsupportedOperationException(
                "TODO: integrate Dojah — see https://docs.dojah.io/docs");
    }
}
