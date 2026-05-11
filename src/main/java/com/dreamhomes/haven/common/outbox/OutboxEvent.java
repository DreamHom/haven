package com.dreamhomes.haven.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.offer.model.Offer;
/**
 * One row per cross-service event. Written in the same transaction as the domain row
 * that produced it; a scheduled {@link OutboxRelay} ships it to Kafka later and marks
 * {@code publishedAt} only after the broker acks. Crash anywhere in between → the relay
 * re-publishes on its next tick (at-least-once); consumers dedup by {@code eventId}.
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identifier for the logical event — used as the consumer-side dedup key. */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    /** Which aggregate produced this event ("InspectionRequest", "Offer", …). Useful for routing. */
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    /** Fully-qualified class name of the payload — relay uses it to deserialise before publishing. */
    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String topic;

    /** Used as the Kafka message key — partitions events for ordering on the consumer side. */
    @Column(name = "partition_key", nullable = false, length = 128)
    private String partitionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Null while still pending. Set by the relay after Kafka acks the publish. */
    @Column(name = "published_at")
    private Instant publishedAt;
}
