package com.dreamhomes.haven.domain.inspection.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InspectionEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InspectionEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishInspectionRequested(String topic, InspectionRequestedEvent event) {
        kafkaTemplate.send(topic, event);
    }
}

