package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.notification.model.NotificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InspectionRequestedListener}. Verifies the multi-recipient
 * fan-out behaviour: every inspection notifies the owner; when the listing has an
 * active agent, the agent gets a parallel notification with a deterministic child
 * eventId so dedup remains per-recipient even though the underlying column is
 * globally unique.
 */
@ExtendWith(MockitoExtension.class)
class InspectionRequestedListenerTest {

    @Mock NotificationApi notificationApi;
    @Mock ListingService listingService;
    @Mock Acknowledgment ack;

    InspectionRequestedListener listener;

    @BeforeEach
    void setUp() {
        listener = new InspectionRequestedListener(notificationApi, listingService);
    }

    @Test
    void notifiesOwnerOnlyWhenNoActiveAgentAssigned() {
        InspectionRequestedEvent event = eventWith(/*ownerId=*/7L, /*listingId=*/42L);
        when(listingService.activeAgentUserId(42L)).thenReturn(null);

        listener.onInspectionRequested(event, ack);

        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_REQUESTED, 7L, event);
        verify(notificationApi, times(1)).recordAsync(any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void notifiesOwnerAndActiveAgentWithDistinctEventIdsSoBothRowsCommit() {
        InspectionRequestedEvent event = eventWith(/*ownerId=*/7L, /*listingId=*/42L);
        when(listingService.activeAgentUserId(42L)).thenReturn(9L);

        listener.onInspectionRequested(event, ack);

        // Owner: original eventId for backwards compat with the existing dedup row.
        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_REQUESTED, 7L, event);
        // Agent: derived child eventId so the global UNIQUE on notifications.event_id
        // doesn't block the second insert.
        UUID childEventId = UUID.nameUUIDFromBytes(
                (event.eventId() + ":agent").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verify(notificationApi).recordAsync(eq(childEventId),
                eq(NotificationKind.INSPECTION_REQUESTED), eq(9L), eq(event));
        verify(ack).acknowledge();
    }

    @Test
    void doesNotFanOutToAgentWhenAgentEqualsOwnerToAvoidDoubleNotifying() {
        InspectionRequestedEvent event = eventWith(/*ownerId=*/7L, /*listingId=*/42L);
        // Defensive: an owner could in theory also be the assigned agent in a corner case.
        when(listingService.activeAgentUserId(42L)).thenReturn(7L);

        listener.onInspectionRequested(event, ack);

        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_REQUESTED, 7L, event);
        verify(notificationApi, times(1)).recordAsync(any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    private static InspectionRequestedEvent eventWith(Long ownerId, Long listingId) {
        return new InspectionRequestedEvent(
                UUID.randomUUID(), /*inspectionRequestId=*/999L, /*slotId=*/50L,
                listingId, ownerId, /*applicantId=*/100L,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.now());
    }
}
