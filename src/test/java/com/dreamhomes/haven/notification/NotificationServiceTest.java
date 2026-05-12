package com.dreamhomes.haven.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.notification.model.NotificationSource;

/**
 * Unit tests for {@link NotificationService} as the implementation of {@link NotificationApi}.
 * Tests both flavours of the API:
 * <ul>
 *   <li>{@code recordSync} — caller-supplied map, no eventId, source=SYNC.</li>
 *   <li>{@code recordAsync} — eventId-based dedup, arbitrary payload object, source=ASYNC_KAFKA.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, new ObjectMapper().findAndRegisterModules(), new com.dreamhomes.haven.notification.NotificationSseEmitters());
    }

    @Test
    void recordSyncPersistsNotificationWithSyncSourceAndSerialisedMapPayload() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listingId", 7L);
        payload.put("status", "CLOSED");

        service.recordSync(NotificationKind.LISTING_TAKEDOWN, /*recipientUserId=*/50L, payload);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification saved = cap.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(50L);
        assertThat(saved.getKind()).isEqualTo(NotificationKind.LISTING_TAKEDOWN);
        assertThat(saved.getSource()).isEqualTo(NotificationSource.SYNC);
        assertThat(saved.getEventId()).isNull();
        assertThat(saved.getPayload()).contains("\"listingId\":7").contains("\"status\":\"CLOSED\"");
    }

    @Test
    void recordAsyncPersistsNotificationWithEventIdAndAsyncKafkaSource() {
        UUID eventId = UUID.randomUUID();
        when(notificationRepository.existsByEventId(eventId)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        // Any record/POJO works as the payload — Jackson serialises by reflection.
        record Body(Long offerId, String currency) {}
        Body event = new Body(123L, "NGN");

        service.recordAsync(eventId, NotificationKind.OFFER_SUBMITTED, /*recipientUserId=*/99L, event);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification saved = cap.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getRecipientId()).isEqualTo(99L);
        assertThat(saved.getKind()).isEqualTo(NotificationKind.OFFER_SUBMITTED);
        assertThat(saved.getSource()).isEqualTo(NotificationSource.ASYNC_KAFKA);
        assertThat(saved.getPayload()).contains("\"offerId\":123").contains("\"currency\":\"NGN\"");
    }

    @Test
    void recordAsyncSkipsDuplicateBasedOnEventId() {
        UUID eventId = UUID.randomUUID();
        when(notificationRepository.existsByEventId(eventId)).thenReturn(true);

        service.recordAsync(eventId, NotificationKind.INSPECTION_REQUESTED, 99L, "anything");

        verify(notificationRepository, never()).save(any());
    }
}
