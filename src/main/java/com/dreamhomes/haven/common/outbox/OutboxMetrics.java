package com.dreamhomes.haven.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Exposes {@code haven.outbox.unpublished} as a Micrometer gauge reading the count of
 * outbox rows still awaiting publication. Ops can alert when the value stays > 0 for
 * longer than the relay's poll interval — that means the relay is wedged or Kafka is
 * down.
 */
@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    static final String GAUGE_NAME = "haven.outbox.unpublished";

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxRepository;

    @PostConstruct
    void registerGauges() {
        Gauge.builder(GAUGE_NAME, outboxRepository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox rows still awaiting publication to Kafka")
                .register(meterRegistry);
    }
}
