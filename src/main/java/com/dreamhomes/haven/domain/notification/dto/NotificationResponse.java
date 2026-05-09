package com.dreamhomes.haven.domain.notification.dto;

import com.dreamhomes.haven.domain.notification.model.NotificationType;
import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationResponse(
        Long id,
        
        @NotNull 
        NotificationType type,
        
        @NotBlank 
        String payload,

        @NotNull 
        Instant createdAt
) {}