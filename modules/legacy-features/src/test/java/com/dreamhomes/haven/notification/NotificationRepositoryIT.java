package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.common.AbstractPostgresIT;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class NotificationRepositoryIT extends AbstractPostgresIT {

    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void persistsNotificationRoundTripThroughTheSchema() {
        Long recipientId = newUser().getId();

        Notification saved = notificationRepository.save(Notification.builder()
                .recipientId(recipientId).kind(NotificationKind.INSPECTION_REQUESTED)
                .payload("{\"hello\":\"world\"}")
                .createdAt(Instant.now()).build());

        Notification found = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getRecipientId()).isEqualTo(recipientId);
        assertThat(found.getKind()).isEqualTo(NotificationKind.INSPECTION_REQUESTED);
        assertThat(found.getPayload()).isEqualTo("{\"hello\":\"world\"}");
        assertThat(found.getReadAt()).isNull();
    }

    @Test
    void findByRecipientReturnsRecipientsNotificationsNewestFirst() {
        Long recipientA = newUser().getId();
        Long recipientB = newUser().getId();

        Notification older = notificationRepository.saveAndFlush(notification(recipientA,
                Instant.parse("2026-01-01T00:00:00Z")));
        Notification newer = notificationRepository.saveAndFlush(notification(recipientA,
                Instant.parse("2026-01-02T00:00:00Z")));
        notificationRepository.saveAndFlush(notification(recipientB,
                Instant.parse("2026-01-03T00:00:00Z")));

        List<Notification> result = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientA);

        assertThat(result).extracting(Notification::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    private Notification notification(Long recipientId, Instant createdAt) {
        return Notification.builder()
                .recipientId(recipientId).kind(NotificationKind.INSPECTION_REQUESTED)
                .payload("{}").createdAt(createdAt).build();
    }

    private User newUser() {
        return userRepository.save(User.builder()
                .email("notif-recipient-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.OWNER).fullName("Recipient")
                .tokenVersion(1).createdAt(Instant.now()).build());
    }
}
