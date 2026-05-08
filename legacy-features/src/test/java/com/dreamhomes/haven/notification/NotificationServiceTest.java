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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    void recordsInspectionRequestedWithEventIdAndSourceAsyncKafka() {
        UUID eventId = UUID.randomUUID();
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                eventId, 1234L, 50L, 7L, 99L, 100L,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.parse("2026-05-15T08:30:00Z"));
        when(notificationRepository.existsByEventId(eventId)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordInspectionRequested(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getRecipientId()).isEqualTo(99L);
        assertThat(saved.getKind()).isEqualTo(NotificationKind.INSPECTION_REQUESTED);
        assertThat(saved.getSource()).isEqualTo(NotificationSource.ASYNC_KAFKA);
        assertThat(saved.getPayload()).contains("\"inspectionRequestId\":1234");
    }

    @Test
    void skipsDuplicateInspectionEventBasedOnEventId() {
        UUID eventId = UUID.randomUUID();
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                eventId, 1234L, 50L, 7L, 99L, 100L,
                Instant.now(), Instant.now().plusSeconds(3600), Instant.now());
        when(notificationRepository.existsByEventId(eventId)).thenReturn(true);

        service.recordInspectionRequested(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void recordsOfferSubmittedWithEventIdAndSourceAsyncKafka() {
        UUID eventId = UUID.randomUUID();
        OfferSubmittedEvent event = new OfferSubmittedEvent(
                eventId, 123L, 7L, 99L, 100L,
                new BigDecimal("75000000.00"), "NGN",
                Instant.parse("2026-05-15T08:30:00Z"));
        when(notificationRepository.existsByEventId(eventId)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordOfferSubmitted(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getKind()).isEqualTo(NotificationKind.OFFER_SUBMITTED);
        assertThat(saved.getSource()).isEqualTo(NotificationSource.ASYNC_KAFKA);
    }

    @Test
    void skipsDuplicateOfferEventBasedOnEventId() {
        UUID eventId = UUID.randomUUID();
        OfferSubmittedEvent event = new OfferSubmittedEvent(
                eventId, 123L, 7L, 99L, 100L,
                new BigDecimal("100"), "NGN", Instant.now());
        when(notificationRepository.existsByEventId(eventId)).thenReturn(true);

        service.recordOfferSubmitted(event);

        verify(notificationRepository, never()).save(any());
    }
}
