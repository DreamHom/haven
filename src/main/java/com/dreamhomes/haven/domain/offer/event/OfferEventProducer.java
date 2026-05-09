package com.dreamhomes.haven.domain.offer.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OfferEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOfferSubmitted(String topic, OfferSubmittedEvent event) {
        kafkaTemplate.send(topic, event);
    }
}

