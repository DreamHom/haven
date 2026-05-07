package com.dreamhomes.haven.domain.inspection.event;

import java.time.Instant;

public record InspectionRequestedEvent(
        Long inspectionRequestId,
        Long slotId,
        Long applicantId,
        Instant requestedAt
) {}

