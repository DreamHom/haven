package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.user.service.UserAdminService;
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
import com.dreamhomes.haven.verification.exception.VerificationAlreadyDecidedException;
import com.dreamhomes.haven.verification.exception.VerificationNotFoundException;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;
import com.dreamhomes.haven.verification.service.VerificationAdminService;

/**
 * VerificationAdminService owns the decision write that admin-impl used to perform
 * directly when it had a compile dependency on verification-impl. The exception was
 * retired by routing the decision through {@link VerificationAdminService}; this is its
 * impl side.
 *
 * <p>Tests cover the per-type badge-flip dispatch (which entity gets stamped), the
 * status transition + decision metadata, the already-decided guard, and the rejection
 * reason validation. Audit log + notification + metric writes belong to admin-impl
 * and are tested there.</p>
 */
@ExtendWith(MockitoExtension.class)
class VerificationAdminServiceTest {

    @Mock VerificationRepository verificationRepository;
    @Mock UserAdminService userAdminService;
    @Mock PropertyService propertyService;

    VerificationAdminService service;

    @BeforeEach
    void setUp() {
        service = new VerificationAdminService(verificationRepository, userAdminService, propertyService, new com.dreamhomes.haven.verification.mapping.VerificationAdminMapperImpl());
    }

    @Test
    void approvingFlipsStatusAndStampsDecisionMetadata() {
        Verification pending = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.approve(7L, 99L, "looks good");

        ArgumentCaptor<Verification> cap = ArgumentCaptor.forClass(Verification.class);
        verify(verificationRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(cap.getValue().getDecidedByAdminId()).isEqualTo(7L);
        assertThat(cap.getValue().getDecidedAt()).isNotNull();
        assertThat(cap.getValue().getDecisionReason()).isEqualTo("looks good");
    }

    @Test
    void approvingOwnerIdentityDelegatesIdentityBadgeStampToUserAdminService() {
        Verification pending = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.approve(7L, 99L, null);

        verify(userAdminService).markIdentityVerified(eq(50L), any());
        verify(propertyService, never()).markDocumentsVerified(anyLong(), any());
    }

    @Test
    void approvingApplicantIdentityAlsoStampsIdentityBadgeOnTheTargetUser() {
        Verification pending = pendingFor(VerificationType.APPLICANT_IDENTITY, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.approve(7L, 99L, null);

        verify(userAdminService).markIdentityVerified(eq(50L), any());
    }

    @Test
    void approvingAgentCredentialsDelegatesCredentialStampToUserAdminService() {
        Verification pending = pendingFor(VerificationType.AGENT_CREDENTIALS, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.approve(7L, 99L, null);

        verify(userAdminService).markAgentCredentialVerified(eq(50L), any());
        verify(userAdminService, never()).markIdentityVerified(anyLong(), any());
    }

    @Test
    void approvingPropertyDocumentsDelegatesToPropertyService() {
        Verification pending = pendingFor(VerificationType.PROPERTY_DOCUMENTS, null, 7L);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.approve(8L, 99L, null);

        verify(propertyService).markDocumentsVerified(eq(7L), any());
        verify(userAdminService, never()).markIdentityVerified(anyLong(), any());
        verify(userAdminService, never()).markAgentCredentialVerified(anyLong(), any());
    }

    @Test
    void rejectingStoresReasonAndDoesNotFlipAnyBadge() {
        Verification pending = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.reject(7L, 99L, "Document unreadable");

        ArgumentCaptor<Verification> cap = ArgumentCaptor.forClass(Verification.class);
        verify(verificationRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(cap.getValue().getDecisionReason()).isEqualTo("Document unreadable");

        verify(userAdminService, never()).markIdentityVerified(anyLong(), any());
        verify(userAdminService, never()).markAgentCredentialVerified(anyLong(), any());
        verify(propertyService, never()).markDocumentsVerified(anyLong(), any());
    }

    @Test
    void rejectingRequiresAReasonBeforeAnyRepoLookup() {
        // The reason guard fires first so we never burn a DB roundtrip on a 400 path.
        assertThatThrownBy(() -> service.reject(7L, 99L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void cannotApproveAlreadyDecidedRow() {
        Verification approved = pendingFor(VerificationType.OWNER_IDENTITY, 50L, null);
        approved.setStatus(VerificationStatus.APPROVED);
        approved.setDecidedAt(Instant.now());
        approved.setDecidedByAdminId(7L);
        when(verificationRepository.findById(99L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approve(8L, 99L, null))
                .isInstanceOf(VerificationAlreadyDecidedException.class);

        verify(verificationRepository, never()).save(any());
        verify(userAdminService, never()).markIdentityVerified(anyLong(), any());
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

    private static Verification pendingFor(VerificationType type,
                                           Long targetUserId, Long targetPropertyId) {
        return Verification.builder()
                .id(99L).type(type)
                .submitterUserId(50L)
                .targetUserId(targetUserId).targetPropertyId(targetPropertyId)
                .status(VerificationStatus.PENDING)
                .documentRefs("{}")
                .submittedAt(Instant.now())
                .build();
    }
}
