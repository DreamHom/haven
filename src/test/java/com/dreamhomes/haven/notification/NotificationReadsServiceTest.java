package com.dreamhomes.haven.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.notification.model.NotificationSource;
import com.dreamhomes.haven.notification.exception.NotMyNotificationException;
import com.dreamhomes.haven.notification.exception.NotificationNotFoundException;

/**
 * Read-side of the notification service: list-mine, count-unread, mark-read. Unit-tested
 * separately from {@link NotificationService} so the existing recordX tests stay focused.
 */
@ExtendWith(MockitoExtension.class)
class NotificationReadsServiceTest {

    @Mock NotificationRepository notificationRepository;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, new com.fasterxml.jackson.databind.ObjectMapper(), new com.dreamhomes.haven.notification.NotificationSseEmitters());
    }

    @Test
    void listMineReturnsPaginatedNotificationsForRecipient() {
        Page<Notification> page = new PageImpl<>(
                List.of(stub(1L, 50L, NotificationKind.OFFER_SUBMITTED)),
                PageRequest.of(0, 20), 1);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(50L), any()))
                .thenReturn(page);

        Page<Notification> result = service.listMine(50L, false, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRecipientId()).isEqualTo(50L);
    }

    @Test
    void listMineWithUnreadOnlyFiltersOnReadAtIsNull() {
        when(notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(eq(50L), any()))
                .thenReturn(Page.empty());

        service.listMine(50L, true, PageRequest.of(0, 20));

        verify(notificationRepository).findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(eq(50L), any());
        verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(eq(50L), any());
    }

    @Test
    void countUnreadDelegatesToRepoCount() {
        when(notificationRepository.countByRecipientIdAndReadAtIsNull(50L)).thenReturn(3L);

        long n = service.countUnread(50L);

        assertThat(n).isEqualTo(3);
    }

    @Test
    void markReadByRecipientStampsReadAt() {
        Notification existing = stub(123L, /*recipientId=*/50L, NotificationKind.COMMENT_POSTED);
        when(notificationRepository.findById(123L)).thenReturn(Optional.of(existing));

        service.markRead(/*callerId=*/50L, 123L);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getReadAt()).isNotNull();
    }

    @Test
    void markReadIsIdempotentOnAlreadyReadRowDoesNotResetTimestamp() {
        Instant earlier = Instant.parse("2026-04-01T00:00:00Z");
        Notification existing = stub(123L, 50L, NotificationKind.COMMENT_POSTED);
        existing.setReadAt(earlier);
        when(notificationRepository.findById(123L)).thenReturn(Optional.of(existing));

        service.markRead(50L, 123L);

        // Second call must not save a new readAt — preserves the original "first read at" timestamp.
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadByNonRecipientThrows403() {
        Notification existing = stub(123L, /*recipientId=*/50L, NotificationKind.COMMENT_POSTED);
        when(notificationRepository.findById(123L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.markRead(/*callerId=*/99L, 123L))
                .isInstanceOf(NotMyNotificationException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadOnNonExistentNotificationThrows404() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(50L, 404L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    private static Notification stub(Long id, Long recipientId, NotificationKind kind) {
        return Notification.builder()
                .id(id).recipientId(recipientId).kind(kind)
                .source(NotificationSource.SYNC).payload("{}")
                .createdAt(Instant.now()).build();
    }
}
