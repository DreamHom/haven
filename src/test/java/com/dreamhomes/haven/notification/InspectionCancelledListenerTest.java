package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionCancelledEvent;
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
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link InspectionCancelledListener}. The cancellation can come from
 * any of three parties (applicant, owner, assigned agent) and the listener must notify
 * the OTHER participants — the cancelling user already knows they cancelled.
 */
@ExtendWith(MockitoExtension.class)
class InspectionCancelledListenerTest {

    @Mock NotificationApi notificationApi;
    @Mock Acknowledgment ack;

    InspectionCancelledListener listener;

    @BeforeEach
    void setUp() {
        listener = new InspectionCancelledListener(notificationApi);
    }

    @Test
    void applicantCancelsApprovedRequestNotifiesOwnerAndAgent() {
        InspectionCancelledEvent event = eventCancelledBy(/*applicant*/2L);

        listener.onInspectionCancelled(event, ack);

        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_CANCELLED, /*owner=*/99L, event);
        // Agent gets a derived child id so the global UNIQUE on event_id doesn't block.
        UUID agentId = InspectionRequestedListener.childEventIdFor(event.eventId(), ":agent");
        verify(notificationApi).recordAsync(eq(agentId),
                eq(NotificationKind.INSPECTION_CANCELLED), eq(50L), eq(event));
        // Applicant (the canceller) does NOT get a notification.
        verify(notificationApi, never()).recordAsync(any(), any(), eq(2L), any());
        verify(ack).acknowledge();
    }

    @Test
    void ownerCancelsApprovedRequestNotifiesApplicantAndAgent() {
        InspectionCancelledEvent event = eventCancelledBy(/*owner*/99L);

        listener.onInspectionCancelled(event, ack);

        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_CANCELLED, /*applicant=*/2L, event);
        UUID agentId = InspectionRequestedListener.childEventIdFor(event.eventId(), ":agent");
        verify(notificationApi).recordAsync(eq(agentId),
                eq(NotificationKind.INSPECTION_CANCELLED), eq(50L), eq(event));
        verify(notificationApi, never()).recordAsync(any(), any(), eq(99L), any());
    }

    @Test
    void agentCancelsApprovedRequestNotifiesApplicantAndOwner() {
        InspectionCancelledEvent event = eventCancelledBy(/*agent*/50L);

        listener.onInspectionCancelled(event, ack);

        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_CANCELLED, /*applicant=*/2L, event);
        UUID ownerChild = InspectionRequestedListener.childEventIdFor(event.eventId(), ":owner");
        verify(notificationApi).recordAsync(eq(ownerChild),
                eq(NotificationKind.INSPECTION_CANCELLED), eq(99L), eq(event));
        verify(notificationApi, never()).recordAsync(any(), any(), eq(50L), any());
    }

    @Test
    void applicantCancelsPendingRequestWithNoAgentNotifiesOwnerOnly() {
        InspectionCancelledEvent event = new InspectionCancelledEvent(
                UUID.randomUUID(), 10L, 1L, 7L,
                /*applicantId=*/2L, /*ownerId=*/99L, /*agentUserId=*/null,
                /*cancelledByUserId=*/2L, "Work blew up", Instant.now());

        listener.onInspectionCancelled(event, ack);

        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_CANCELLED, 99L, event);
        verify(notificationApi, org.mockito.Mockito.times(1))
                .recordAsync(any(), any(), any(), any());
    }

    private static InspectionCancelledEvent eventCancelledBy(Long cancelledByUserId) {
        return new InspectionCancelledEvent(
                UUID.randomUUID(), /*inspectionRequestId=*/10L, /*slotId=*/1L,
                /*listingId=*/7L, /*applicantId=*/2L, /*ownerId=*/99L,
                /*agentUserId=*/50L, cancelledByUserId, "Reason", Instant.now());
    }
}
