package com.dreamhomes.haven.common.outbox;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves we expose the unpublished outbox count under the documented Micrometer name
 * and that it tracks the actual row count.
 */
class OutboxMetricsIT extends AbstractPostgresIT {

    @Autowired MeterRegistry meterRegistry;
    @Autowired OutboxEventRepository outboxRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        outboxRepository.deleteAll();
    }

    @Test
    void exposesUnpublishedCountUnderHavenOutboxUnpublished() {
        outboxRepository.saveAndFlush(unpublishedRow());
        outboxRepository.saveAndFlush(unpublishedRow());

        Double value = meterRegistry.find("haven.outbox.unpublished").gauge().value();

        assertThat(value).isEqualTo(2.0);
    }

    @Test
    void publishedRowsDoNotCountTowardsTheGauge() {
        outboxRepository.saveAndFlush(unpublishedRow());
        outboxRepository.saveAndFlush(publishedRow());
        outboxRepository.saveAndFlush(publishedRow());

        Double value = meterRegistry.find("haven.outbox.unpublished").gauge().value();

        assertThat(value).isEqualTo(1.0);
    }

    private static OutboxEvent unpublishedRow() {
        return baseRow().publishedAt(null).build();
    }

    private static OutboxEvent publishedRow() {
        return baseRow().publishedAt(Instant.now()).build();
    }

    private static OutboxEvent.OutboxEventBuilder baseRow() {
        return OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("Test").aggregateId(1L)
                .eventType("test.Event")
                .topic("test").partitionKey("1")
                .payload("{}")
                .createdAt(Instant.now());
    }
}
