package com.dreamhomes.haven.domain.offer.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OfferEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OfferEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOfferSubmitted(String topic, OfferSubmittedEvent event) {
        kafkaTemplate.send(topic, event);
    }
}

