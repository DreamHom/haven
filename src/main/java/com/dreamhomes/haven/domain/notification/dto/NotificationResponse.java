package com.dreamhomes.haven.domain.notification.dto;

import com.dreamhomes.haven.domain.notification.model.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String payload,
        Instant createdAt
) {}

