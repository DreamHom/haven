package com.dreamhomes.haven.common.outbox;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what we own: round-trip through the schema, and that
 * {@code claimBatchForPublishing} returns only unpublished rows in created-at order
 * (the SKIP LOCKED clause is a PG primitive, not ours to test).
 */
class OutboxEventRepositoryIT extends AbstractPostgresIT {

    @Autowired
    OutboxEventRepository repository;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void persistsRowAndReadsItBack() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent saved = repository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("InspectionRequest")
                .aggregateId(123L)
                .eventType("com.dreamhomes.haven.inspection.events.InspectionRequestedEvent")
                .topic("inspection.requested.v1")
                .partitionKey("7")
                .payload("{\"hello\":\"world\"}")
                .createdAt(Instant.now())
                .build());

        OutboxEvent found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventId()).isEqualTo(eventId);
        assertThat(found.getPayload()).isEqualTo("{\"hello\":\"world\"}");
        assertThat(found.getPublishedAt()).isNull();
    }

    @Test
    void claimBatchOnlyReturnsUnpublishedRowsOldestFirst() {
        OutboxEvent oldUnpublished = repository.saveAndFlush(row(Instant.parse("2026-01-01T00:00:00Z"), null));
        OutboxEvent newUnpublished = repository.saveAndFlush(row(Instant.parse("2026-01-02T00:00:00Z"), null));
        repository.saveAndFlush(row(Instant.parse("2026-01-03T00:00:00Z"), Instant.parse("2026-01-03T00:00:01Z")));

        List<OutboxEvent> batch = repository.claimBatchForPublishing(50);

        assertThat(batch).extracting(OutboxEvent::getId)
                .containsExactly(oldUnpublished.getId(), newUnpublished.getId());
    }

    @Test
    void claimBatchRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            repository.saveAndFlush(row(Instant.now().plusSeconds(i), null));
        }

        List<OutboxEvent> batch = repository.claimBatchForPublishing(3);

        assertThat(batch).hasSize(3);
    }

    private static OutboxEvent row(Instant createdAt, Instant publishedAt) {
        return OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("Test").aggregateId(1L)
                .eventType("test.Event")
                .topic("test").partitionKey("1")
                .payload("{}")
                .createdAt(createdAt).publishedAt(publishedAt)
                .build();
    }
}
