package com.dreamhomes.haven.admin.dto;

import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AuditTargetType;

import java.time.Instant;

/**
 * Public projection of {@code AdminAuditLog} for {@code GET /api/admin/audit-logs}.
 * {@code metadata} is the raw JSON the write-side stored — clients deserialise
 * per-{@link AdminAction} as needed.
 */
public record AdminAuditLogResponse(
        Long id,
        Long adminId,
        AdminAction action,
        AuditTargetType targetType,
        Long targetId,
        String metadata,
        Instant createdAt) {
}
