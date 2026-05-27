package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionDecidedEvent;
import com.dreamhomes.haven.notification.model.NotificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link InspectionDecidedListener}. Closes Gap B of post-session-tasks
 * Item 7 — the applicant must hear back when the owner approves or declines, instead
 * of having to refresh.
 */
@ExtendWith(MockitoExtension.class)
class InspectionDecidedListenerTest {

    @Mock NotificationApi notificationApi;
    @Mock Acknowledgment ack;

    InspectionDecidedListener listener;

    @BeforeEach
    void setUp() {
        listener = new InspectionDecidedListener(notificationApi);
    }

    @Test
    void approvedDecisionNotifiesApplicantWithInspectionApprovedKind() {
        InspectionDecidedEvent event = new InspectionDecidedEvent(
                UUID.randomUUID(), /*inspectionRequestId=*/10L, /*slotId=*/1L,
                /*listingId=*/7L, /*applicantId=*/2L,
                InspectionDecidedEvent.Decision.APPROVED, /*reason=*/null,
                Instant.now());

        listener.onInspectionDecided(event, ack);

        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_APPROVED, /*recipient=*/2L, event);
        verify(ack).acknowledge();
    }

    @Test
    void declinedDecisionNotifiesApplicantWithInspectionDeclinedKindAndCarriesReason() {
        InspectionDecidedEvent event = new InspectionDecidedEvent(
                UUID.randomUUID(), 10L, 1L, 7L, 2L,
                InspectionDecidedEvent.Decision.DECLINED,
                "Slot conflicts with another visit", Instant.now());

        listener.onInspectionDecided(event, ack);

        // Listener passes the full event as the payload — the reason rides along.
        verify(notificationApi).recordAsync(event.eventId(),
                NotificationKind.INSPECTION_DECLINED, 2L, event);
        verify(ack).acknowledge();
    }
}
