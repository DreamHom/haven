package com.dreamhomes.haven.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        Long id,
        UUID eventId,
        Long recipientId,
        NotificationKind kind,
        NotificationSource source,
        String payload,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getEventId(), n.getRecipientId(),
                n.getKind(), n.getSource(), n.getPayload(),
                n.getReadAt(), n.getCreatedAt());
    }
}
