package com.dreamhomes.haven.domain.inspection.dto;

import com.dreamhomes.haven.domain.inspection.model.InspectionStatus;
import java.time.Instant;

public record InspectionRequestResponse(
        Long id,
        Long slotId,
        Long applicantId,
        InspectionStatus status,
        Instant createdAt
) {}

