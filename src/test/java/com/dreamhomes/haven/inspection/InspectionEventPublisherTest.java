package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InspectionEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishesEventToTheVersionedTopicKeyedBySlotId() {
        InspectionEventPublisher publisher = new InspectionEventPublisher(kafkaTemplate);
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                1L, 50L, 7L, 99L, 100L,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.now());

        publisher.publishInspectionRequested(event);

        // Slot id is the partition key — every event for the same slot goes to the same
        // partition, preserving per-slot ordering on the consumer side.
        verify(kafkaTemplate).send(
                eq(InspectionRequestedEvent.TOPIC),
                eq("50"),
                eq(event));
    }
}
