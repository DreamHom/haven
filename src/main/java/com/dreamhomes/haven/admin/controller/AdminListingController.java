package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.admin.dto.AdminListingResponse;
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
            @PathVariable Long id) {
        return toAdminResponse(adminListingService.approve(principal.userId(), id));
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
