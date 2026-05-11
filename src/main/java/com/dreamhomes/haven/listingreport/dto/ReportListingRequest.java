package com.dreamhomes.haven.listingreport.dto;

import com.dreamhomes.haven.listingreport.model.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/listings/{id}/report}.
 *
 * <p>{@code reason} is required and constrained to the {@link ReportReason} enum.
 * {@code details} is optional free-text context; capped at 1000 chars to keep abuse
 * payloads from blowing up the queue. Trim happens in the service before insert.
 */
public record ReportListingRequest(
        @NotNull(message = "reason is required")
        ReportReason reason,

        @Size(max = 1000, message = "details must be 1000 characters or fewer")
        String details
) {
}
