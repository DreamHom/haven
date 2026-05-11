package com.dreamhomes.haven.listingreport.dto;

import com.dreamhomes.haven.listingreport.model.ReportReason;

/**
 * Service-layer input for filing a report. Already past controller validation —
 * fields are non-blank where the wire DTO declared them required.
 */
public record ReportListingCommand(ReportReason reason, String details) {
}
