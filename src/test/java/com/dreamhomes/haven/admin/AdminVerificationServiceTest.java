package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.verification.VerificationAdminService;
import com.dreamhomes.haven.verification.VerificationAdminView;
import com.dreamhomes.haven.verification.VerificationAlreadyDecidedException;
import com.dreamhomes.haven.verification.VerificationNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminVerificationService is a thin orchestrator after the VerificationAdminService
 * extraction:
 * <ul>
 *   <li>delegate the decision (status flip + badge stamp) to {@link VerificationAdminService}</li>
 *   <li>write the audit log row + sync notification + metric</li>
 * </ul>
 * The badge-flipping mechanics (which entity gets stamped per VerificationType) are
 * covered in {@code VerificationAdminServiceTest} inside {@code feature/verification/impl}.
 */
@ExtendWith(MockitoExtension.class)
class AdminVerificationServiceTest {

    @Mock VerificationAdminService verificationAdminService;
    @Mock NotificationApi notificationApi;
    @Mock AdminAuditLogRepository auditLogRepository;

    AdminVerificationService service;

    @BeforeEach
    void setUp() {
        service = new AdminVerificationService(verificationAdminService, notificationApi,
                auditLogRepository, new ObjectMapper(),
                new AdminMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void approvingDelegatesAndWritesAuditPlusSyncNotification() {
        VerificationAdminView decided = decidedView(VerificationType.OWNER_IDENTITY,
                VerificationStatus.APPROVED, 50L, null);
        when(verificationAdminService.approve(7L, 99L, null)).thenReturn(decided);

        VerificationAdminView result = service.approve(7L, 99L, null);

        assertThat(result).isSameAs(decided);

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getAdminId()).isEqualTo(7L);
        assertThat(auditCap.getValue().getAction()).isEqualTo(AdminAction.VERIFICATION_APPROVED);
        assertThat(auditCap.getValue().getTargetType()).isEqualTo(AuditTargetType.VERIFICATION);
        assertThat(auditCap.getValue().getTargetId()).isEqualTo(99L);

        verify(notificationApi).recordSync(eq(NotificationKind.VERIFICATION_APPROVED), eq(50L), any());
    }

    @Test
    void rejectingDelegatesAndStoresReasonInAuditMetadata() {
        VerificationAdminView decided = decidedView(VerificationType.OWNER_IDENTITY,
                VerificationStatus.REJECTED, 50L, "Document unreadable");
        when(verificationAdminService.reject(7L, 99L, "Document unreadable")).thenReturn(decided);

        service.reject(7L, 99L, "Document unreadable");

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getMetadata()).contains("Document unreadable");

        verify(notificationApi).recordSync(eq(NotificationKind.VERIFICATION_REJECTED), eq(50L), any());
    }

    @Test
    void carriesPropertyTargetIdInAuditMetadataForPropertyVerifications() {
        VerificationAdminView decided = decidedView(VerificationType.PROPERTY_DOCUMENTS,
                VerificationStatus.APPROVED, null, null);
        when(verificationAdminService.approve(8L, 99L, null)).thenReturn(decided);

        service.approve(8L, 99L, null);

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getMetadata()).contains("targetPropertyId");
    }

    @Test
    void propagatesVerificationAlreadyDecidedFromApiAndDoesNotEmitSideEffects() {
        when(verificationAdminService.approve(8L, 99L, null))
                .thenThrow(new VerificationAlreadyDecidedException(99L, VerificationStatus.APPROVED));

        assertThatThrownBy(() -> service.approve(8L, 99L, null))
                .isInstanceOf(VerificationAlreadyDecidedException.class);

        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void propagatesVerificationNotFoundFromApi() {
        when(verificationAdminService.approve(7L, 999L, null))
                .thenThrow(new VerificationNotFoundException(999L));

        assertThatThrownBy(() -> service.approve(7L, 999L, null))
                .isInstanceOf(VerificationNotFoundException.class);
    }

    @Test
    void rejectingPropagatesIllegalArgumentFromApiWhenReasonBlank() {
        when(verificationAdminService.reject(7L, 99L, "  "))
                .thenThrow(new IllegalArgumentException("Rejection reason is required"));

        assertThatThrownBy(() -> service.reject(7L, 99L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(auditLogRepository, never()).save(any());
        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    private static VerificationAdminView decidedView(VerificationType type,
                                                     VerificationStatus status,
                                                     Long targetUserId,
                                                     String reason) {
        Long targetPropertyId = type == VerificationType.PROPERTY_DOCUMENTS ? 7L : null;
        return new VerificationAdminView(
                99L, type, status,
                50L, targetUserId, targetPropertyId,
                "{}", Instant.now(),
                Instant.now(), 7L, reason);
    }
}
