package com.dreamhomes.haven.verification.automation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MOCKED v1 — every check returns {@link AutomatedCheckResult#STATUS_PASSED} with
 * score 0.95 and plausible mock-extracted fields. The point is to wire the
 * integration boundary end-to-end (provider abstraction + persisted automation rows
 * + admin queue visibility) so v2 can drop in a real provider via env var without
 * any caller-side changes.
 *
 * <p>Activated by default ({@code matchIfMissing = true}); production deploys can
 * override via {@code HAVEN_VERIFICATION_PROVIDER=smile-id} to swap to the sister
 * scaffolded provider once it's implemented.
 */
@Component
@ConditionalOnProperty(name = "haven.verification.provider", havingValue = "mock", matchIfMissing = true)
public class MockVerificationProvider implements VerificationProvider {

    public static final String PROVIDER_NAME = "MOCK";
    private static final BigDecimal MOCK_SCORE = new BigDecimal("0.95");
    private static final String MOCK_RAW_RESPONSE =
            "{\"provider\":\"MOCK\",\"status\":\"PASSED\",\"score\":0.95,\"note\":\"v1 mock — always passes\"}";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public AutomatedCheckResult verifyOwnerIdentity(OwnerIdentityCheckRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nin", "12345678901");
        fields.put("nameMatch", 0.98);
        fields.put("documentAuthenticity", 0.96);
        return mocked(fields, "mock-owner-" + req.verificationId());
    }

    @Override
    public AutomatedCheckResult verifyAgentCredentials(AgentCredentialsCheckRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("licenseNumber", "REA-LAG-00782");
        fields.put("licenseStatus", "ACTIVE");
        fields.put("nameMatch", 0.97);
        return mocked(fields, "mock-agent-" + req.verificationId());
    }

    @Override
    public AutomatedCheckResult verifyApplicantIdentity(ApplicantIdentityCheckRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nin", "98765432101");
        fields.put("nameMatch", 0.99);
        return mocked(fields, "mock-applicant-" + req.verificationId());
    }

    @Override
    public AutomatedCheckResult verifyPropertyDocuments(PropertyDocumentCheckRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("titleType", "C_OF_O");
        fields.put("registryNumber", "LAG/2024/00123");
        fields.put("documentAuthenticity", 0.94);
        fields.put("addressMatch", 0.95);
        return mocked(fields, "mock-property-" + req.verificationId());
    }

    private AutomatedCheckResult mocked(Map<String, Object> extractedFields, String reference) {
        return new AutomatedCheckResult(
                AutomatedCheckResult.STATUS_PASSED,
                MOCK_SCORE,
                extractedFields,
                reference,
                MOCK_RAW_RESPONSE,
                Instant.now());
    }
}
