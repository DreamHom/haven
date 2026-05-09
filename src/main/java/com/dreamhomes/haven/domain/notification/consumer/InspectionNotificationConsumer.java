package com.dreamhomes.haven.domain.notification.consumer;

import com.dreamhomes.haven.domain.inspection.event.InspectionRequestedEvent;
import com.dreamhomes.haven.domain.notification.model.NotificationType;
import com.dreamhomes.haven.domain.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InspectionNotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topics.inspection-requested:INSPECTION_REQUESTED}", groupId = "${kafka.consumer.group:haven}")
    public void onInspectionRequested(InspectionRequestedEvent event) throws JsonProcessingException {

        notificationService.create(
                event.applicantId(),
                NotificationType.INSPECTION_REQUESTED,
                objectMapper.writeValueAsString(event)
        );
    }

}

