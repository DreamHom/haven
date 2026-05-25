package com.dreamhomes.haven.verification.automation;

/**
 * Strategy interface for the first-pass automated verification step (Item 20 in
 * {@code docs/demo-prep/post-session-tasks.md}). Each verification submission dispatches
 * to one of the four methods based on its {@code VerificationType}; the result is
 * persisted alongside the verification row so admins can see what the provider extracted
 * before they decide.
 *
 * <h2>v1 vs v2</h2>
 * <p>v1 has exactly one active implementation: {@link MockVerificationProvider}. The
 * two sister implementations ({@code SmileIdVerificationProvider},
 * {@code DojahVerificationProvider}) are scaffolded with detailed TODO comments so v2
 * is a config change ({@code HAVEN_VERIFICATION_PROVIDER=smile-id}) plus filling in the
 * method bodies, not a refactor of any code that depends on this interface.
 *
 * <h2>Implementations resolve via Spring conditional config</h2>
 * <p>Each provider carries {@code @ConditionalOnProperty(name = "haven.verification.provider", havingValue = "...")}
 * so exactly one bean is active at boot. Default (and {@code matchIfMissing}) is
 * {@code mock} — production deploys override via env var.
 */
public interface VerificationProvider {

    /** Stable name used for logging + persisted in {@code provider_name}. e.g. "MOCK", "SMILE_ID". */
    String name();

    AutomatedCheckResult verifyOwnerIdentity(OwnerIdentityCheckRequest req);

    AutomatedCheckResult verifyAgentCredentials(AgentCredentialsCheckRequest req);

    AutomatedCheckResult verifyApplicantIdentity(ApplicantIdentityCheckRequest req);

    AutomatedCheckResult verifyPropertyDocuments(PropertyDocumentCheckRequest req);
}
