package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.property.PropertyApi;
import com.dreamhomes.haven.user.AgentProfile;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.dreamhomes.haven.verification.Verification;
import com.dreamhomes.haven.verification.VerificationAlreadyDecidedException;
import com.dreamhomes.haven.verification.VerificationNotFoundException;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.VerificationStatus;
import com.dreamhomes.haven.verification.VerificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminVerificationServiceTest {

    @Mock VerificationRepository verificationRepository;
    @Mock UserRepository userRepository;
    @Mock AgentProfileRepository agentProfileRepository;
    @Mock PropertyApi propertyApi;
    @Mock NotificationApi notificationApi;
    @Mock AdminAuditLogRepository auditLogRepository;

    AdminVerificationService service;

    @BeforeEach
    void setUp() {
        service = new AdminVerificationService(verificationRepository, userRepository,
                agentProfileRepository, propertyApi, notificationApi,
                auditLogRepository, new ObjectMapper(),
                new AdminMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void approvingOwnerIdentityFlipsIdentityVerifiedAtAndKeepsRowInApprovedState() {
        Verification pending = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        User submitter = ownerUser(50L);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(50L)).thenReturn(Optional.of(submitter));
        when(verificationRepository.save(any(Verification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approve(7L, 99L, null);

        ArgumentCaptor<Verification> verifCap = ArgumentCaptor.forClass(Verification.class);
        verify(verificationRepository).save(verifCap.capture());
        Verification approved = verifCap.getValue();
        assertThat(approved.getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(approved.getDecidedByAdminId()).isEqualTo(7L);
        assertThat(approved.getDecidedAt()).isNotNull();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getIdentityVerifiedAt()).isNotNull();
    }

    @Test
    void approvingApplicantIdentityFlipsIdentityVerifiedAtOnTheSubmitter() {
        Verification pending = pendingFor(VerificationType.APPLICANT_IDENTITY, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(50L))
                .thenReturn(Optional.of(applicantUser(50L)));

        service.approve(7L, 99L, null);

        verify(userRepository).save(any(User.class));
        verify(agentProfileRepository, never()).save(any());
        verify(propertyApi, never()).markDocumentsVerified(anyLong(), any());
    }

    @Test
    void approvingAgentCredentialsFlipsCredentialVerifiedAtOnTheAgentProfile() {
        Verification pending = pendingFor(VerificationType.AGENT_CREDENTIALS, 50L, null);
        AgentProfile profile = AgentProfile.builder()
                .userId(50L).licenseNumber("LIC").createdAt(Instant.now()).build();
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(agentProfileRepository.findById(50L)).thenReturn(Optional.of(profile));

        service.approve(7L, 99L, null);

        ArgumentCaptor<AgentProfile> cap = ArgumentCaptor.forClass(AgentProfile.class);
        verify(agentProfileRepository).save(cap.capture());
        assertThat(cap.getValue().getCredentialVerifiedAt()).isNotNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void approvingPropertyDocumentsFlipsDocumentsVerifiedAtOnTheProperty() {
        Verification pending = pendingFor(VerificationType.PROPERTY_DOCUMENTS, null, 7L);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.approve(8L, 99L, null);

        verify(propertyApi).markDocumentsVerified(eq(7L), any());
    }

    @Test
    void everyApprovalWritesAuditLogRowAndSyncNotificationForSubmitter() {
        Verification pending = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(50L)).thenReturn(Optional.of(ownerUser(50L)));

        service.approve(7L, 99L, null);

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        AdminAuditLog audit = auditCap.getValue();
        assertThat(audit.getAdminId()).isEqualTo(7L);
        assertThat(audit.getAction()).isEqualTo(AdminAction.VERIFICATION_APPROVED);
        assertThat(audit.getTargetType()).isEqualTo(AuditTargetType.VERIFICATION);
        assertThat(audit.getTargetId()).isEqualTo(99L);

        verify(notificationApi).recordSync(eq(NotificationKind.VERIFICATION_APPROVED), eq(50L), any());
    }

    @Test
    void rejectingStoresReasonAndDoesNotFlipBadge() {
        Verification pending = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.reject(7L, 99L, "Document unreadable");

        ArgumentCaptor<Verification> verifCap = ArgumentCaptor.forClass(Verification.class);
        verify(verificationRepository).save(verifCap.capture());
        Verification rejected = verifCap.getValue();
        assertThat(rejected.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(rejected.getDecisionReason()).isEqualTo("Document unreadable");
        assertThat(rejected.getDecidedByAdminId()).isEqualTo(7L);

        verify(userRepository, never()).save(any());
        verify(agentProfileRepository, never()).save(any());
        verify(propertyApi, never()).markDocumentsVerified(anyLong(), any());

        verify(notificationApi).recordSync(eq(NotificationKind.VERIFICATION_REJECTED), eq(50L), any());
    }

    @Test
    void cannotDecideAlreadyDecidedRowToPreventDoubleApproval() {
        Verification approved = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        approved.setStatus(VerificationStatus.APPROVED);
        approved.setDecidedAt(Instant.now());
        approved.setDecidedByAdminId(7L);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approve(8L, 99L, null))
                .isInstanceOf(VerificationAlreadyDecidedException.class);

        verify(verificationRepository, never()).save(any());
        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void cannotRejectAlreadyDecidedRow() {
        Verification rejected = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        rejected.setStatus(VerificationStatus.REJECTED);
        rejected.setDecidedAt(Instant.now());
        rejected.setDecidedByAdminId(7L);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.reject(8L, 99L, "any reason"))
                .isInstanceOf(VerificationAlreadyDecidedException.class);
    }

    @Test
    void approvingNonExistentVerificationThrows404() {
        when(verificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(7L, 999L, null))
                .isInstanceOf(VerificationNotFoundException.class);
    }

    @Test
    void rejectingRequiresAReason() {
        // Reason check fires before any repo lookup — no need to stub findById.
        assertThatThrownBy(() -> service.reject(7L, 99L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(verificationRepository, never()).save(any());
    }

    private static Verification pendingFor(VerificationType type, Long targetUserId, Long targetPropertyId) {
        return Verification.builder()
                .id(99L).type(type)
                .submitterUserId(50L)
                .targetUserId(targetUserId).targetPropertyId(targetPropertyId)
                .status(VerificationStatus.PENDING)
                .documentRefs("{}")
                .submittedAt(Instant.now())
                .build();
    }

    private static User ownerUser(Long id) {
        return User.builder().id(id).email("o@x").passwordHash("x").fullName("O")
                .role(Role.OWNER).tokenVersion(1).createdAt(Instant.now()).build();
    }

    private static User applicantUser(Long id) {
        return User.builder().id(id).email("a@x").passwordHash("x").fullName("A")
                .role(Role.APPLICANT).tokenVersion(1).createdAt(Instant.now()).build();
    }
}
