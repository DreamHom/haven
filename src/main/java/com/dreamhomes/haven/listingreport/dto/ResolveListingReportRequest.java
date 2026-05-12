package com.dreamhomes.haven.listingreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/admin/listing-reports/{id}/resolve} and
 * {@code /dismiss}. {@code note} is required so the queue can't be silently
 * flushed — every disposition leaves a record.
 */
public record ResolveListingReportRequest(
        @NotBlank @Size(min = 1, max = 1000) String note
) {
}
