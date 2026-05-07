package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void recordsInspectionRequestedNotificationForTheListingOwner() {
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                1234L, 50L, 7L, 99L, 100L,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.parse("2026-05-15T08:30:00Z"));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordInspectionRequested(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(99L);
        assertThat(saved.getKind()).isEqualTo(NotificationKind.INSPECTION_REQUESTED);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getReadAt()).isNull();
        assertThat(saved.getPayload())
                .contains("\"inspectionRequestId\":1234")
                .contains("\"slotId\":50")
                .contains("\"listingId\":7")
                .contains("\"applicantId\":100");
    }

    @Test
    void recordsOfferSubmittedNotificationForTheListingOwner() {
        OfferSubmittedEvent event = new OfferSubmittedEvent(
                123L, 7L, 99L, 100L,
                new BigDecimal("75000000.00"), "NGN",
                Instant.parse("2026-05-15T08:30:00Z"));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordOfferSubmitted(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(99L);
        assertThat(saved.getKind()).isEqualTo(NotificationKind.OFFER_SUBMITTED);
        assertThat(saved.getPayload())
                .contains("\"offerId\":123")
                .contains("\"listingId\":7")
                .contains("\"applicantId\":100")
                .contains("\"amount\":75000000.00");
    }
}
