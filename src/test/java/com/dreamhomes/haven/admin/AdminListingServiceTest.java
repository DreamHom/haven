package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingResponse;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminListingServiceTest {

    @Mock ListingService listingService;
    @Mock NotificationApi notificationApi;
    @Mock AdminAuditLogRepository auditLogRepository;

    AdminListingService service;

    @BeforeEach
    void setUp() {
        service = new AdminListingService(listingService, notificationApi,
                auditLogRepository, new ObjectMapper(),
                new AdminMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void approvingListingStampsApprovedAtWritesAuditAndNotifiesOwner() {
        // Pre-mark: not approved. Post-mark (after markApproved): approved.
        when(listingService.findById(11L))
                .thenReturn(listing(11L, 50L, ListingStatus.LIVE, null))
                .thenReturn(listing(11L, 50L, ListingStatus.LIVE, Instant.now()));

        service.approve(7L, 11L);

        verify(listingService).markApproved(eq(11L), any());

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getAction()).isEqualTo(AdminAction.LISTING_APPROVED);
        assertThat(auditCap.getValue().getTargetType()).isEqualTo(AuditTargetType.LISTING);
        assertThat(auditCap.getValue().getTargetId()).isEqualTo(11L);

        verify(notificationApi).recordSync(eq(NotificationKind.LISTING_APPROVED), eq(50L), any());
    }

    @Test
    void approvingAlreadyApprovedListingThrowsConflict() {
        when(listingService.findById(11L)).thenReturn(listing(11L, 50L, ListingStatus.LIVE, Instant.now()));

        assertThatThrownBy(() -> service.approve(7L, 11L))
                .isInstanceOf(ListingAlreadyApprovedException.class);

        verify(listingService, never()).markApproved(anyLong(), any());
        verify(auditLogRepository, never()).save(any());
        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    @Test
    void approvingNonExistentListingThrows404() {
        when(listingService.findById(404L)).thenThrow(new ListingNotFoundException(404L));

        assertThatThrownBy(() -> service.approve(7L, 404L))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void takedownTransitionsLiveListingToClosedAndNotifiesOwner() {
        when(listingService.findById(11L))
                .thenReturn(listing(11L, 50L, ListingStatus.LIVE, null))
                .thenReturn(listing(11L, 50L, ListingStatus.CLOSED, null));

        service.takedown(7L, 11L, "Reported as fraudulent");

        verify(listingService).forceStatus(eq(11L), eq(ListingStatus.CLOSED), any());

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getAction()).isEqualTo(AdminAction.LISTING_TAKEDOWN);
        assertThat(auditCap.getValue().getMetadata()).contains("Reported as fraudulent");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(notificationApi).recordSync(eq(NotificationKind.LISTING_TAKEDOWN), eq(50L), payloadCap.capture());
        assertThat(payloadCap.getValue()).containsEntry("reason", "Reported as fraudulent");
    }

    @Test
    void takedownOfPausedListingAlsoTransitionsToClosed() {
        when(listingService.findById(11L))
                .thenReturn(listing(11L, 50L, ListingStatus.PAUSED, null))
                .thenReturn(listing(11L, 50L, ListingStatus.CLOSED, null));

        service.takedown(7L, 11L, "policy violation");

        verify(listingService).forceStatus(eq(11L), eq(ListingStatus.CLOSED), any());
    }

    @Test
    void takedownOfAlreadyClosedListingThrowsConflict() {
        when(listingService.findById(11L)).thenReturn(listing(11L, 50L, ListingStatus.CLOSED, null));

        assertThatThrownBy(() -> service.takedown(7L, 11L, "any"))
                .isInstanceOf(ListingAlreadyClosedException.class);

        verify(listingService, never()).forceStatus(anyLong(), any(), any());
    }

    @Test
    void takedownRequiresNonEmptyReason() {
        assertThatThrownBy(() -> service.takedown(7L, 11L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(listingService, never()).findById(anyLong());
    }

    private static ListingResponse listing(Long id, Long ownerId, ListingStatus status, Instant approvedAt) {
        Instant now = Instant.now();
        return new ListingResponse(id, 1L, ownerId, ListingType.SALE,
                new BigDecimal("80000000.00"), "NGN", null, null, null,
                status, approvedAt, 0L, now, now, null);
    }
}
