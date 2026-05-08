package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyNotFoundException;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.PropertyType;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceSubmitTest {

    @Mock VerificationRepository verificationRepository;
    @Mock UserRepository userRepository;
    @Mock PropertyRepository propertyRepository;

    VerificationService service;

    @BeforeEach
    void setUp() {
        service = new VerificationService(verificationRepository, userRepository,
                propertyRepository, new ObjectMapper());
    }

    @Test
    void ownerIdentitySubmissionPersistsPendingRowTargetingTheSubmitter() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));
        when(verificationRepository.existsByTypeAndTargetUserIdAndStatus(
                VerificationType.OWNER_IDENTITY, 50L, VerificationStatus.PENDING))
                .thenReturn(false);
        when(verificationRepository.save(any(Verification.class))).thenAnswer(inv -> inv.getArgument(0));

        Verification result = service.submit(50L, new SubmitVerificationCommand(
                VerificationType.OWNER_IDENTITY, null, idDocs()));

        ArgumentCaptor<Verification> cap = ArgumentCaptor.forClass(Verification.class);
        verify(verificationRepository).save(cap.capture());
        Verification saved = cap.getValue();
        assertThat(saved.getType()).isEqualTo(VerificationType.OWNER_IDENTITY);
        assertThat(saved.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(saved.getSubmitterUserId()).isEqualTo(50L);
        assertThat(saved.getTargetUserId()).isEqualTo(50L);
        assertThat(saved.getTargetPropertyId()).isNull();
        assertThat(saved.getDocumentRefs()).contains("\"NIN\"").contains("\"AB1234567\"");
        assertThat(saved.getDecidedAt()).isNull();
        assertThat(saved.getDecidedByAdminId()).isNull();
        assertThat(saved.getSubmittedAt()).isNotNull();
        assertThat(result).isSameAs(saved);
    }

    @Test
    void agentCredentialsSubmissionRequiresAgentRole() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.AGENT_CREDENTIALS, null, idDocs())))
                .isInstanceOf(VerificationRoleMismatchException.class);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void applicantIdentitySubmissionRequiresApplicantRole() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.AGENT)));

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.APPLICANT_IDENTITY, null, idDocs())))
                .isInstanceOf(VerificationRoleMismatchException.class);
    }

    @Test
    void ownerIdentitySubmissionRequiresOwnerRole() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.APPLICANT)));

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.OWNER_IDENTITY, null, idDocs())))
                .isInstanceOf(VerificationRoleMismatchException.class);
    }

    @Test
    void propertyDocumentsSubmissionTargetsThePropertyNotTheSubmitter() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(property(7L, 50L)));
        when(verificationRepository.existsByTypeAndTargetPropertyIdAndStatus(
                VerificationType.PROPERTY_DOCUMENTS, 7L, VerificationStatus.PENDING))
                .thenReturn(false);
        when(verificationRepository.save(any(Verification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(50L, new SubmitVerificationCommand(
                VerificationType.PROPERTY_DOCUMENTS, 7L, propertyDocs()));

        ArgumentCaptor<Verification> cap = ArgumentCaptor.forClass(Verification.class);
        verify(verificationRepository).save(cap.capture());
        assertThat(cap.getValue().getTargetUserId()).isNull();
        assertThat(cap.getValue().getTargetPropertyId()).isEqualTo(7L);
        assertThat(cap.getValue().getSubmitterUserId()).isEqualTo(50L);
    }

    @Test
    void propertyDocumentsSubmissionRejectsCallerWhoDoesNotOwnTheProperty() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(property(7L, /*ownerId=*/99L)));

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.PROPERTY_DOCUMENTS, 7L, propertyDocs())))
                .isInstanceOf(VerificationRoleMismatchException.class);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void propertyDocumentsSubmissionThrowsWhenPropertyDoesNotExist() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));
        when(propertyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.PROPERTY_DOCUMENTS, 404L, propertyDocs())))
                .isInstanceOf(PropertyNotFoundException.class);
    }

    @Test
    void rejectsDuplicatePendingSubmissionForSameUserAndType() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));
        when(verificationRepository.existsByTypeAndTargetUserIdAndStatus(
                VerificationType.OWNER_IDENTITY, 50L, VerificationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.OWNER_IDENTITY, null, idDocs())))
                .isInstanceOf(DuplicatePendingVerificationException.class);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicatePendingPropertyDocsSubmission() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(property(7L, 50L)));
        when(verificationRepository.existsByTypeAndTargetPropertyIdAndStatus(
                VerificationType.PROPERTY_DOCUMENTS, 7L, VerificationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.PROPERTY_DOCUMENTS, 7L, propertyDocs())))
                .isInstanceOf(DuplicatePendingVerificationException.class);
    }

    @Test
    void propertyDocumentsRequirePropertyId() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, Role.OWNER)));

        assertThatThrownBy(() -> service.submit(50L, new SubmitVerificationCommand(
                VerificationType.PROPERTY_DOCUMENTS, null, propertyDocs())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static User user(Long id, Role role) {
        return User.builder().id(id).email("u" + id + "@example.com")
                .passwordHash("x").fullName("U " + id).role(role)
                .tokenVersion(1).createdAt(Instant.now()).build();
    }

    private static Property property(Long id, Long ownerId) {
        return Property.builder().id(id).ownerId(ownerId)
                .type(PropertyType.HOUSE).address("addr").createdAt(Instant.now()).build();
    }

    private static Map<String, Object> idDocs() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "NIN");
        m.put("ref", "AB1234567");
        return m;
    }

    private static Map<String, Object> propertyDocs() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "C_OF_O");
        m.put("ref", "DOC-9182");
        return m;
    }
}
