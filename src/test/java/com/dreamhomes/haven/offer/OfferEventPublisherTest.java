package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OfferEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publishesOfferSubmittedToVersionedTopicKeyedByOfferId() {
        OfferEventPublisher publisher = new OfferEventPublisher(kafkaTemplate);
        OfferSubmittedEvent event = new OfferSubmittedEvent(
                123L, 7L, 99L, 100L,
                new BigDecimal("75000000.00"), "NGN",
                Instant.now());

        publisher.publishOfferSubmitted(event);

        verify(kafkaTemplate).send(
                eq(OfferSubmittedEvent.TOPIC),
                eq("123"),
                eq(event));
    }
}
