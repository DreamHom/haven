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

    @Test
    void exposesOneHavenOutboxDltGaugePerDltTopic() {
        // The depth value depends on broker state and is asserted in the listener ITs;
        // here we only confirm the gauge family is registered for both expected topics
        // so a future code change can't silently drop it.
        var dltGauges = meterRegistry.find("haven.outbox.dlt").gauges();

        assertThat(dltGauges).extracting(g -> g.getId().getTag("topic"))
                .containsExactlyInAnyOrder(
                        "inspection.requested.v1.DLT",
                        "offer.submitted.v1.DLT");
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
