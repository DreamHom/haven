package com.dreamhomes.haven.verification.automation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockVerificationProviderTest {

    private final MockVerificationProvider provider = new MockVerificationProvider();

    @Test
    void nameIsMockSoPersistedRowsAreUnambiguous() {
        assertThat(provider.name()).isEqualTo("MOCK");
    }

    @Test
    void verifyOwnerIdentityReturnsPassedWithExtractedNinAndNameMatch() {
        AutomatedCheckResult result = provider.verifyOwnerIdentity(
                new OwnerIdentityCheckRequest(99L, 50L, "{}"));

        assertThat(result.status()).isEqualTo(AutomatedCheckResult.STATUS_PASSED);
        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.95"));
        assertThat(result.extractedFields()).containsKeys("nin", "nameMatch");
        assertThat(result.providerReference()).contains("99");
        assertThat(result.rawResponse()).contains("\"provider\":\"MOCK\"");
        assertThat(result.runAt()).isNotNull();
    }

    @Test
    void verifyAgentCredentialsReturnsPassedWithLicenseFields() {
        AutomatedCheckResult result = provider.verifyAgentCredentials(
                new AgentCredentialsCheckRequest(100L, 60L, "{}"));

        assertThat(result.status()).isEqualTo(AutomatedCheckResult.STATUS_PASSED);
        assertThat(result.extractedFields()).containsKeys("licenseNumber", "licenseStatus");
    }

    @Test
    void verifyApplicantIdentityReturnsPassedWithNinField() {
        AutomatedCheckResult result = provider.verifyApplicantIdentity(
                new ApplicantIdentityCheckRequest(101L, 70L, "{}"));

        assertThat(result.status()).isEqualTo(AutomatedCheckResult.STATUS_PASSED);
        assertThat(result.extractedFields()).containsKey("nin");
    }

    @Test
    void verifyPropertyDocumentsReturnsPassedWithTitleAndRegistryFields() {
        AutomatedCheckResult result = provider.verifyPropertyDocuments(
                new PropertyDocumentCheckRequest(102L, 50L, 7L, "{}"));

        assertThat(result.status()).isEqualTo(AutomatedCheckResult.STATUS_PASSED);
        assertThat(result.extractedFields()).containsKeys("titleType", "registryNumber");
    }
}
