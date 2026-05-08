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
    public static InspectionResponse from(InspectionRequest r) {
        return new InspectionResponse(
                r.getId(), r.getSlotId(), r.getApplicantId(),
                r.getStatus(), r.getNotes(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
