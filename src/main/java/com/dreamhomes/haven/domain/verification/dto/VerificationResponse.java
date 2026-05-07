package com.dreamhomes.haven.domain.verification.dto;

import com.dreamhomes.haven.domain.verification.model.VerificationStatus;
import com.dreamhomes.haven.domain.verification.model.VerificationType;
import java.time.Instant;

public record VerificationResponse(
        Long id,
        Long subjectUserId,
        Long propertyId,
        VerificationType type,
        VerificationStatus status,
        String documentUrl,
        Instant createdAt
) {}

