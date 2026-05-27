package com.dreamhomes.haven.verification.automation;

import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomatedVerificationServiceTest {

    @Mock VerificationProvider provider;
    @Mock VerificationAutomationResultRepository resultRepository;

    AutomatedVerificationService service;

    @BeforeEach
    void setUp() {
        service = new AutomatedVerificationService(provider, resultRepository, new ObjectMapper());
        when(provider.name()).thenReturn("MOCK");
    }

    @Test
    void runChecksForOwnerVerificationCallsOwnerIdentityProviderMethodAndPersistsRow() {
        Verification ownerVerification = sample(VerificationType.OWNER_IDENTITY, null);
        AutomatedCheckResult result = new AutomatedCheckResult(
                AutomatedCheckResult.STATUS_PASSED,
                new BigDecimal("0.95"),
                Map.of("nin", "12345678901"),
                "mock-owner-99", "{\"x\":1}", Instant.now());
        when(provider.verifyOwnerIdentity(any())).thenReturn(result);

        List<AutomatedCheckResult> results = service.runChecksFor(ownerVerification);

        verify(provider).verifyOwnerIdentity(any());
        verify(provider, never()).verifyAgentCredentials(any());
        ArgumentCaptor<VerificationAutomationResult> cap =
                ArgumentCaptor.forClass(VerificationAutomationResult.class);
        verify(resultRepository).save(cap.capture());
        VerificationAutomationResult saved = cap.getValue();
        assertThat(saved.getVerificationId()).isEqualTo(99L);
        assertThat(saved.getCheckType()).isEqualTo("OWNER_IDENTITY");
        assertThat(saved.getProviderName()).isEqualTo("MOCK");
        assertThat(saved.getStatus()).isEqualTo("PASSED");
        assertThat(saved.getScore()).isEqualByComparingTo(new BigDecimal("0.95"));
        assertThat(saved.getExtractedFields()).contains("\"nin\":\"12345678901\"");
        assertThat(results).containsExactly(result);
    }

    @Test
    void runChecksForAgentVerificationCallsAgentCredentialsProviderMethod() {
        Verification agentVerification = sample(VerificationType.AGENT_CREDENTIALS, null);
        AutomatedCheckResult result = new AutomatedCheckResult(
                AutomatedCheckResult.STATUS_PASSED,
                new BigDecimal("0.95"),
                Map.of("licenseNumber", "REA-LAG-00782"),
                "mock-agent-99", "{}", Instant.now());
        when(provider.verifyAgentCredentials(any())).thenReturn(result);

        service.runChecksFor(agentVerification);

        verify(provider).verifyAgentCredentials(any());
        verify(provider, never()).verifyOwnerIdentity(any());
    }

    @Test
    void runChecksForPropertyDocsVerificationPassesPropertyIdIntoTheProviderRequest() {
        Verification propVerification = sample(VerificationType.PROPERTY_DOCUMENTS, 7L);
        AutomatedCheckResult result = new AutomatedCheckResult(
                AutomatedCheckResult.STATUS_PASSED,
                new BigDecimal("0.95"),
                Map.of("titleType", "C_OF_O"),
                "mock-property-99", "{}", Instant.now());
        when(provider.verifyPropertyDocuments(any())).thenReturn(result);

        service.runChecksFor(propVerification);

        ArgumentCaptor<PropertyDocumentCheckRequest> cap =
                ArgumentCaptor.forClass(PropertyDocumentCheckRequest.class);
        verify(provider).verifyPropertyDocuments(cap.capture());
        assertThat(cap.getValue().propertyId()).isEqualTo(7L);
        assertThat(cap.getValue().verificationId()).isEqualTo(99L);
    }

    private static Verification sample(VerificationType type, Long propertyId) {
        return Verification.builder()
                .id(99L).type(type)
                .submitterUserId(50L)
                .targetUserId(propertyId == null ? 50L : null)
                .targetPropertyId(propertyId)
                .status(VerificationStatus.PENDING)
                .documentRefs("{\"kind\":\"NIN\",\"ref\":\"AB1234567\"}")
                .submittedAt(Instant.now())
                .build();
    }
}
