package com.dreamhomes.haven.domain.notification.consumer;

import com.dreamhomes.haven.domain.notification.model.NotificationType;
import com.dreamhomes.haven.domain.notification.service.NotificationService;
import com.dreamhomes.haven.domain.offer.event.OfferSubmittedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OfferNotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public OfferNotificationConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topics.offer-submitted:OFFER_SUBMITTED}", groupId = "${kafka.consumer.group:haven}")
    public void onOfferSubmitted(OfferSubmittedEvent event) throws JsonProcessingException {
        notificationService.create(
                event.applicantId(),
                NotificationType.OFFER_SUBMITTED,
                objectMapper.writeValueAsString(event)
        );
    }
}

