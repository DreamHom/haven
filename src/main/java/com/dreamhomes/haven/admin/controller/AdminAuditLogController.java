package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.admin.dto.AdminAuditLogResponse;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.admin.service.AdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Admin audit-log read surface. Every admin write (verification approve / reject,
 * user suspend / reactivate, listing takedown / approve, review takedown) already
 * appends a row to {@code admin_audit_log} via {@link AdminAuditService#record}.
 * This endpoint is the missing reader — Dayo's persona audit Story 7.
 *
 * <p>All filters are optional. Pass none for the full chronological log (newest
 * first). Use {@code from} / {@code to} (ISO-8601) to restrict the time window.</p>
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminAuditLogController {

    private final AdminAuditService adminAuditService;

    @Operation(
            summary = "Read the admin audit log",
            description = """
                    Returns the append-only admin audit log — every admin write \
                    produces exactly one row here. Every filter is optional.

                    **Filters**:
                    - `actorId` — restrict to actions by a specific admin
                    - `action` — restrict to a specific action (VERIFICATION_APPROVED, USER_SUSPENDED, etc.)
                    - `targetType` — USER, LISTING, VERIFICATION, REVIEW
                    - `targetId` — combine with `targetType` to scope to a single subject ("everything that happened to user X")
                    - `from` / `to` — ISO-8601 timestamps for a time window

                    **Role gate**: `ADMIN` only.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated audit log entries, newest first."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminAuditLogResponse> search(
            @Parameter(description = "Restrict to actions by this admin.")
            @RequestParam(required = false) Long actorId,
            @Parameter(description = "Restrict to a specific action.")
            @RequestParam(required = false) AdminAction action,
            @Parameter(description = "Restrict to a target type (USER, LISTING, VERIFICATION, REVIEW).")
            @RequestParam(required = false) AuditTargetType targetType,
            @Parameter(description = "Restrict to a single target id (combine with targetType).")
            @RequestParam(required = false) Long targetId,
            @Parameter(description = "Inclusive lower bound (ISO-8601 instant).")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Inclusive upper bound (ISO-8601 instant).")
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return adminAuditService.list(actorId, action, targetType, targetId, from, to, pageable);
    }
}
