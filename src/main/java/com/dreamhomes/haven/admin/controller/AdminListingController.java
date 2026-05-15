package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.admin.dto.AdminListingResponse;
import com.dreamhomes.haven.admin.dto.AdminListingLeadResponse;
import com.dreamhomes.haven.admin.dto.ListingModerationSnapshotResponse;
import com.dreamhomes.haven.admin.dto.TakedownListingRequest;
import com.dreamhomes.haven.admin.service.AdminListingService;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/listings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminListingController {

    private final AdminListingService adminListingService;

    @Operation(
            summary = "Admin listing catalogue",
            description = """
                    Paginated view of listings across the platform for moderation dashboards. \
                    Optional `status` filter (e.g. `TAKEN_DOWN`, `LIVE`). Newest first.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated listings."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public Page<ListingResponse> catalog(
            @Parameter(description = "Optional listing status filter.")
            @RequestParam(required = false) com.dreamhomes.haven.listing.model.ListingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return adminListingService.catalog(status, pageable);
    }

    @Operation(
            summary = "Moderation snapshot for a listing",
            description = """
                    Returns the full listing + property payload even when the listing is \
                    `TAKEN_DOWN` and therefore invisible on public `GET /api/listings/{id}`. \
                    Includes gallery photo count and the latest takedown audit snippet when present.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snapshot assembled."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/moderation-snapshot")
    public ListingModerationSnapshotResponse moderationSnapshot(
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long id) {
        return adminListingService.moderationSnapshot(id);
    }

    @Operation(
            summary = "List leads for a listing (moderation)",
            description = """
                    Paginated applicant interest rows for a listing. Contact fields are always \
                    included for admin review (unlike the owner endpoint before reveal).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated leads."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/leads")
    public Page<AdminListingLeadResponse> listingLeads(
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return adminListingService.listingLeads(id, pageable);
    }

    @Operation(
            summary = "Re-publish a previously taken-down listing",
            description = """
                    Reverses an admin takedown — the listing returns to `OPEN` (or whatever \
                    the prior status was). Stamps an `APPROVED` row in the audit log.

                    **Role gate**: `ADMIN` only.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Listing re-published.",
                    content = @Content(schema = @Schema(implementation = AdminListingResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/approve")
    public AdminListingResponse approve(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long id,
            @Valid @RequestBody(required = false)
            com.dreamhomes.haven.admin.dto.RepublishListingRequest request) {
        String reason = request == null ? null : request.reason();
        return toAdminResponse(adminListingService.approve(principal.userId(), id, reason));
    }

    @Operation(
            summary = "Take down a listing for policy violation",
            description = """
                    Removes the listing from public discovery (`GET /listings` excludes it; \
                    `GET /listings/{id}` returns 404). The action is reversible via the \
                    approve endpoint.

                    **Side effects**:
                    - Audit log row written with the supplied reason.
                    - Notification fired to the listing's owner.

                    **Reason**: required, non-empty (validated). The reason becomes part of \
                    the audit record and the owner's notification body.

                    **Role gate**: `ADMIN`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Listing taken down.",
                    content = @Content(
                            schema = @Schema(implementation = AdminListingResponse.class),
                            examples = @ExampleObject(name = "TakenDown", value = """
                                    { "id": 17, "propertyId": 42, "ownerId": 7,
                                      "status": "TAKEN_DOWN",
                                      "approvedAt": null,
                                      "updatedAt": "2026-05-10T16:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/takedown")
    public AdminListingResponse takedown(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long id,
            @Valid @RequestBody TakedownListingRequest request) {
        return toAdminResponse(adminListingService.takedown(
                principal.userId(), id, request.reason()));
    }

    private static AdminListingResponse toAdminResponse(ListingResponse l) {
        return new AdminListingResponse(l.id(), l.propertyId(), l.ownerId(),
                l.status(), l.approvedAt(), l.updatedAt());
    }
}
