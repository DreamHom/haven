package com.dreamhomes.haven.inspection.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.inspection.dto.InspectionResponse;
import com.dreamhomes.haven.inspection.dto.RequestInspectionCommand;
import com.dreamhomes.haven.inspection.dto.RequestInspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
@Tag(name = "Inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    @Operation(
            summary = "Claim an inspection slot",
            description = """
                    Records an `InspectionRequest` against the chosen slot. The slot \
                    becomes unavailable to other applicants the moment this row commits — \
                    a concurrent claim against the same slot loses with 409.

                    **Side effects**:
                    - Writes an outbox row `inspection.requested.v1` in the same DB \
                      transaction. The outbox relay publishes to Kafka after commit.
                    - Notifications fire to the listing's owner **and** any assigned agent.

                    **Role gate**: `APPLICANT`. The applicant is the calling user — never \
                    accept "applicantId" in the request body.

                    **No fee** is charged at any point in this flow. PRD non-negotiable.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Inspection request recorded; slot now claimed by this applicant.",
                    content = @Content(
                            schema = @Schema(implementation = InspectionResponse.class),
                            examples = @ExampleObject(name = "ClaimedSlot", value = """
                                    { "id": 33, "slotId": 12, "applicantId": 89,
                                      "status": "REQUESTED", "notes": "Coming with my husband.",
                                      "createdAt": "2026-05-10T12:00:00Z",
                                      "updatedAt": "2026-05-10T12:00:00Z" }
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
    @PreAuthorize("hasRole('APPLICANT')")
    public InspectionResponse request(@AuthenticationPrincipal JwtPrincipal principal,
                                      @Valid @RequestBody RequestInspectionRequest body) {
        InspectionRequest saved = inspectionService.requestSlot(principal.userId(),
                new RequestInspectionCommand(body.slotId(), body.notes()));
        return toResponse(saved);
    }

    @Operation(
            summary = "List my inspection requests",
            description = """
                    Returns the caller's own inspection bookings, newest first. Closes the
                    gap Temi flagged: after booking a slot there was no way to see your
                    upcoming inspections, no status, no recovery.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of the caller's bookings."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine")
    public Page<InspectionResponse> listMine(@AuthenticationPrincipal JwtPrincipal principal,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return inspectionService.listMine(principal.userId(), pageable).map(this::toResponse);
    }

    private InspectionResponse toResponse(InspectionRequest r) {
        return new InspectionResponse(r.getId(), r.getSlotId(), r.getApplicantId(),
                r.getStatus(), r.getNotes(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
