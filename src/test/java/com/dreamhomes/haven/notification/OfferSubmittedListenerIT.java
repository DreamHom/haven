package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;

/**
 * Proves the wiring we own: an {@link OfferSubmittedEvent} on the topic results in a
 * Notification row for the listing owner.
 */
class OfferSubmittedListenerIT extends AbstractPostgresIT {

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void eventOnTopicResultsInNotificationRowForOwner() {
        User owner = userRepository.save(User.builder()
                .email("offer-listener-owner-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.OWNER).fullName("Owner")
                .tokenVersion(1).createdAt(Instant.now()).build());

        OfferSubmittedEvent event = new OfferSubmittedEvent(
                java.util.UUID.randomUUID(),
                999L, 7L, owner.getId(), 100L,
                new BigDecimal("75000000.00"), "NGN",
                Instant.now());

        kafkaTemplate.send(OfferSubmittedEvent.TOPIC, String.valueOf(event.listingId()), event);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifs = notificationRepository
                    .findByRecipientIdOrderByCreatedAtDesc(owner.getId());
            assertThat(notifs).hasSize(1);
            assertThat(notifs.get(0).getKind()).isEqualTo(NotificationKind.OFFER_SUBMITTED);
            assertThat(notifs.get(0).getPayload()).contains("\"offerId\":999");
        });
    }
}
