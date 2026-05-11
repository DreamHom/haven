package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;

/**
 * Proves the wiring we own: when an {@link InspectionRequestedEvent} is published to
 * the topic, the {@code InspectionRequestedListener} consumes it and the
 * {@code NotificationService} persists a row for the listing owner.
 */
class InspectionRequestedListenerIT extends AbstractPostgresIT {

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;


    @Test
    void eventPublishedToTopicResultsInNotificationRowForOwner() {
        // Persist a user that the notification will reference (FK target).
        User owner = userRepository.save(User.builder()
                .email("listener-owner-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.OWNER).fullName("Owner")
                .displayName("Owner")
                .tokenVersion(1).createdAt(Instant.now()).build());

        InspectionRequestedEvent event = new InspectionRequestedEvent(
                UUID.randomUUID(),
                999L, 50L, 7L, owner.getId(), 100L,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.now());

        kafkaTemplate.send(InspectionRequestedEvent.TOPIC, String.valueOf(event.listingId()), event);

        // Listener processes asynchronously — poll until the row appears, fail after 10s.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(owner.getId());
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getKind()).isEqualTo(NotificationKind.INSPECTION_REQUESTED);
            assertThat(notifications.get(0).getPayload()).contains("\"inspectionRequestId\":999");
        });
    }
}
