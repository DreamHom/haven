package com.dreamhomes.haven.listingreport.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse;
import com.dreamhomes.haven.listingreport.dto.ResolveListingReportRequest;
import com.dreamhomes.haven.listingreport.model.ListingReportStatus;
import com.dreamhomes.haven.listingreport.model.ReportReason;
import com.dreamhomes.haven.listingreport.service.ListingReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin queue + disposition endpoints for user-filed listing reports.
 *
 * <p>Persona audit (Dayo): user reports were being persisted, but no admin queue
 * surfaced them. This is the missing read-side + disposition pair.</p>
 */
@RestController
@RequestMapping("/api/admin/listing-reports")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminListingReportController {

    private final ListingReportService listingReportService;

    @Operation(
            summary = "List user-filed listing reports",
            description = """
                    Paginated admin queue of listing reports, newest first. Every filter
                    is optional. Default returns ALL statuses — pass `?status=PENDING`
                    for the working queue.

                    **Role gate**: `ADMIN` only.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated reports."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminListingReportResponse> list(
            @Parameter(description = "Filter by lifecycle status.")
            @RequestParam(required = false) ListingReportStatus status,
            @Parameter(description = "Filter by report reason.")
            @RequestParam(required = false) ReportReason reason,
            @Parameter(description = "Restrict to reports against a single listing.")
            @RequestParam(required = false) Long listingId,
            @Parameter(description = "Restrict to reports filed by a single user.")
            @RequestParam(required = false) Long reporterUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        return listingReportService.adminList(status, reason, listingId, reporterUserId, pageable);
    }

    @Operation(
            summary = "Mark a report RESOLVED (admin acted on it)",
            description = """
                    Closes a PENDING report with an admin note ("took the listing down",
                    "suspended the agent", etc.). Reporter is notified that their report
                    was actioned. Returns 409 if the report is already terminal.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report transitioned to RESOLVED."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminListingReportResponse resolve(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ResolveListingReportRequest request) {
        return listingReportService.resolve(principal.userId(), id, request.note());
    }

    @Operation(
            summary = "Mark a report DISMISSED (not actionable)",
            description = """
                    Closes a PENDING report with an admin note explaining why no action
                    was taken. Reporter is notified so they don't think the report
                    vanished into a hole. Returns 409 if the report is already terminal.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report transitioned to DISMISSED."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminListingReportResponse dismiss(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ResolveListingReportRequest request) {
        return listingReportService.dismiss(principal.userId(), id, request.note());
    }
}
