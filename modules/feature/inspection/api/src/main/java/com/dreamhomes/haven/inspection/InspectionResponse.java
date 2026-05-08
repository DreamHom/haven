package com.dreamhomes.haven.inspection;

import java.time.Instant;

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
