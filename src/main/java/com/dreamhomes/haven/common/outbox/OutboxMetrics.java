package com.dreamhomes.haven.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
