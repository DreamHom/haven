package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
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
class AdminListingServiceTest {

    @Mock ListingRepository listingRepository;
    @Mock NotificationApi notificationApi;
    @Mock AdminAuditLogRepository auditLogRepository;

    AdminListingService service;

    @BeforeEach
    void setUp() {
        service = new AdminListingService(listingRepository, notificationApi,
                auditLogRepository, new ObjectMapper(),
                new AdminMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void approvingListingStampsApprovedAtWritesAuditAndNotifiesOwner() {
        when(listingRepository.findById(11L)).thenReturn(Optional.of(listing(11L, 50L, ListingStatus.LIVE, null)));

        service.approve(7L, 11L);

        ArgumentCaptor<Listing> listingCap = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(listingCap.capture());
        assertThat(listingCap.getValue().getApprovedAt()).isNotNull();

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getAction()).isEqualTo(AdminAction.LISTING_APPROVED);
        assertThat(auditCap.getValue().getTargetType()).isEqualTo(AuditTargetType.LISTING);
        assertThat(auditCap.getValue().getTargetId()).isEqualTo(11L);

        verify(notificationApi).recordSync(eq(NotificationKind.LISTING_APPROVED), eq(50L), any());
    }

    @Test
    void approvingAlreadyApprovedListingThrowsConflict() {
        when(listingRepository.findById(11L)).thenReturn(Optional.of(
                listing(11L, 50L, ListingStatus.LIVE, Instant.now())));

        assertThatThrownBy(() -> service.approve(7L, 11L))
                .isInstanceOf(ListingAlreadyApprovedException.class);

        verify(listingRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    @Test
    void approvingNonExistentListingThrows404() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(7L, 404L))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void takedownTransitionsLiveListingToClosedAndNotifiesOwner() {
        when(listingRepository.findById(11L)).thenReturn(Optional.of(listing(11L, 50L, ListingStatus.LIVE, null)));

        service.takedown(7L, 11L, "Reported as fraudulent");

        ArgumentCaptor<Listing> cap = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(cap.getValue().getUpdatedAt()).isNotNull();

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
        when(listingRepository.findById(11L)).thenReturn(Optional.of(listing(11L, 50L, ListingStatus.PAUSED, null)));

        service.takedown(7L, 11L, "policy violation");

        ArgumentCaptor<Listing> cap = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ListingStatus.CLOSED);
    }

    @Test
    void takedownOfAlreadyClosedListingThrowsConflict() {
        when(listingRepository.findById(11L)).thenReturn(Optional.of(listing(11L, 50L, ListingStatus.CLOSED, null)));

        assertThatThrownBy(() -> service.takedown(7L, 11L, "any"))
                .isInstanceOf(ListingAlreadyClosedException.class);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void takedownRequiresNonEmptyReason() {
        assertThatThrownBy(() -> service.takedown(7L, 11L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(listingRepository, never()).findById(any());
    }

    private static Listing listing(Long id, Long ownerId, ListingStatus status, Instant approvedAt) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(id).propertyId(1L).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(status).approvedAt(approvedAt)
                .createdAt(now).updatedAt(now).version(0L).build();
    }
}
