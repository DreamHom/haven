package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OfferEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOfferSubmitted(OfferSubmittedEvent event) {
        kafkaTemplate.send(
                OfferSubmittedEvent.TOPIC,
                String.valueOf(event.offerId()),
                event);
        log.info("Published offer.submitted.v1 offerId={} listingId={}",
                event.offerId(), event.listingId());
    }
}
