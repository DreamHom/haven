package com.dreamhomes.haven.notification.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A notification persisted for one recipient. {@code payload} is JSON text whose
 * shape depends on {@link #kind} — read it in concert with the kind enum.
 *
 * <p>{@code eventId} is the consumer-side dedup key — at-least-once Kafka delivery
 * means the same event can arrive twice; the UNIQUE constraint on this column
 * makes the second insert a no-op (we check {@code existsByEventId} in the service).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Per-event identifier for idempotent inserts. Null only for sync-source notifications. */
    @Column(name = "event_id", unique = true)
    private UUID eventId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private NotificationSource source = NotificationSource.ASYNC_KAFKA;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "read_at")
    private Instant readAt;

    @CreatedDate

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
