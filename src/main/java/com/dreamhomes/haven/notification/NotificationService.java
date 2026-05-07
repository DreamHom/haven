package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records a notification row for the listing owner when an applicant requests an
     * inspection. The payload JSON carries the event verbatim so a future "my notifications"
     * UI can render whatever it needs without re-fetching from the inspection module.
     */
    @Transactional
    public Notification recordInspectionRequested(InspectionRequestedEvent event) {
        Notification saved = notificationRepository.save(Notification.builder()
                .recipientId(event.ownerId())
                .kind(NotificationKind.INSPECTION_REQUESTED)
                .payload(serialize(event))
                .createdAt(Instant.now())
                .build());
        log.info("Recorded notificationId={} kind={} recipientId={}",
                saved.getId(), saved.getKind(), saved.getRecipientId());
        return saved;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // We control the input — this should never happen in practice. If it does,
            // surface as runtime so the listener crashes loudly and the message is retried.
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }
    }
}
