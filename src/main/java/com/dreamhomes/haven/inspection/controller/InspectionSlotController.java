package com.dreamhomes.haven.inspection.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.inspection.dto.CreateSlotCommand;
import com.dreamhomes.haven.inspection.dto.CreateSlotRequest;
import com.dreamhomes.haven.inspection.dto.SlotResponse;
import com.dreamhomes.haven.inspection.mapping.InspectionSlotMapper;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.inspection.service.InspectionSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/listings/{listingId}/slots")
@RequiredArgsConstructor
@Tag(name = "Inspections")
public class InspectionSlotController {

    private final InspectionSlotService slotService;
    private final InspectionSlotMapper inspectionSlotMapper;

    @Operation(
            summary = "Open an inspection slot on a listing",
            description = """
                    Records an `InspectionSlot` window during which applicants can claim an \
                    inspection. Slots are publicly visible to anyone browsing the listing.

                    **Authorisation**: only the listing's owner can open slots today. \
                    Assigned-agent slot creation is a roadmap item — the service layer has \
                    the hook, but the controller still rejects non-owner callers with 403. \
                    Any extension here will be additive.

                    **Overlap constraint**: enforced by a Postgres `EXCLUDE USING GIST` \
                    constraint on `(listing_id, time_range)`. Trying to open a slot that \
                    intersects an existing active slot for the same listing returns 409.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Slot created and publicly visible.",
                    content = @Content(
                            schema = @Schema(implementation = SlotResponse.class),
                            examples = @ExampleObject(name = "WeekendSlot", value = """
                                    { "id": 12, "listingId": 17,
                                      "startsAt": "2026-05-17T10:00:00Z",
                                      "endsAt":   "2026-05-17T10:30:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public SlotResponse create(@AuthenticationPrincipal JwtPrincipal principal,
                               @Parameter(description = "Listing ID.", example = "17")
                               @PathVariable Long listingId,
                               @Valid @RequestBody CreateSlotRequest request) {
        InspectionSlot saved = slotService.create(principal.userId(), listingId,
                new CreateSlotCommand(request.startsAt(), request.endsAt()));
        return inspectionSlotMapper.toResponse(saved);
    }

    @Operation(
            summary = "List available inspection slots for a listing",
            description = """
                    Returns slots that are still open for booking on this listing — slots \
                    already claimed by an inspection request are excluded.

                    Public — no auth required. Cache-Control headers stamped by the \
                    public-cache interceptor.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Available slots for the listing. Empty list is valid.",
                    content = @Content(
                            examples = @ExampleObject(name = "ThreeOpenSlots", value = """
                                    [
                                      { "id": 12, "listingId": 17,
                                        "startsAt": "2026-05-17T10:00:00Z",
                                        "endsAt":   "2026-05-17T10:30:00Z" },
                                      { "id": 13, "listingId": 17,
                                        "startsAt": "2026-05-17T11:00:00Z",
                                        "endsAt":   "2026-05-17T11:30:00Z" }
                                    ]
                                    """))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements // public
    @GetMapping
    public List<SlotResponse> listAvailable(
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long listingId) {
        return slotService.listAvailableForListing(listingId).stream()
                .map(inspectionSlotMapper::toResponse)
                .toList();
    }

}
