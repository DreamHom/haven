package com.dreamhomes.haven.domain.inspection.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InspectionEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInspectionRequested(String topic, InspectionRequestedEvent event) {
        kafkaTemplate.send(topic, event);
    }
}

