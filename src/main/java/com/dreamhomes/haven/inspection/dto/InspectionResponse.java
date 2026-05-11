package com.dreamhomes.haven.inspection.dto;

import java.time.Instant;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;

public record InspectionResponse(
        Long id,
        Long slotId,
        Long applicantId,
        InspectionRequestStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
