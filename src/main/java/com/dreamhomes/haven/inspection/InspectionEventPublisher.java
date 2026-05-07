package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around {@link KafkaTemplate} for inspection events. Centralises topic
 * names + key choice (slot id) so producers don't repeat themselves and consumers can
 * trust per-slot ordering.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInspectionRequested(InspectionRequestedEvent event) {
        kafkaTemplate.send(
                InspectionRequestedEvent.TOPIC,
                String.valueOf(event.slotId()),
                event);
        log.info("Published inspection.requested.v1 for inspectionRequestId={} slotId={}",
                event.inspectionRequestId(), event.slotId());
    }
}
