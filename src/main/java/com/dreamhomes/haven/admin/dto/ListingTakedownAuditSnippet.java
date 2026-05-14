package com.dreamhomes.haven.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Latest admin takedown audit row for a listing (if any).")
public record ListingTakedownAuditSnippet(
        @Schema(description = "Audit log row id.")
        Long auditLogId,
        @Schema(description = "Admin who performed the takedown.")
        Long adminId,
        @Schema(description = "When the takedown was recorded.")
        Instant occurredAt,
        @Schema(description = "Reason captured on the audit row.")
        String reason
) {
}
