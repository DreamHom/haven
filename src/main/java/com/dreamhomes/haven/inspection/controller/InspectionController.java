package com.dreamhomes.haven.inspection.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.inspection.dto.InspectionResponse;
import com.dreamhomes.haven.inspection.dto.AgentExtrasUpdateRequest;
import com.dreamhomes.haven.inspection.dto.AgentRescheduleSlotRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
                                      "status": "PENDING", "notes": "Coming with my husband.",
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
                    Returns the caller's own inspection bookings, newest first. Closes the \
                    gap Temi flagged: after booking a slot there was no way to see your \
                    upcoming inspections, no status, no recovery.

                    **Observed statuses today**: `PENDING` (default after `POST`) or \
                    `CANCELLED` (after the caller's own `DELETE`). `APPROVED` / `DECLINED` \
                    are reserved for the future owner approve/decline endpoint and will not \
                    appear in production responses until that endpoint ships.
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

    @Operation(
            summary = "Cancel my inspection request",
            description = """
                    Withdraws a PENDING inspection request the caller made — status flips to \
                    `CANCELLED`, the slot is freed for other applicants (the partial UQ on \
                    `inspection_requests(slot_id) WHERE status IN ('PENDING','APPROVED')` \
                    is what enforces the slot lock; the cancel drops out of that index).

                    **Failure modes**:
                    - `403` if the caller isn't the applicant on the row.
                    - `404` if the row doesn't exist.
                    - `409` if the row is already in a terminal state (`CANCELLED`, or — once \
                      the future owner approve/decline endpoint ships — `APPROVED` / \
                      `DECLINED`). In production today, the only terminal state reachable is \
                      `CANCELLED`, so a 409 here means "you already cancelled this."

                    Persona audit (Temi) flagged the missing cancel surface.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cancelled; slot freed."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('APPLICANT')")
    public void cancel(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long id) {
        inspectionService.cancel(principal.userId(), id);
    }

    @Operation(summary = "Approve a pending inspection request (owner)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request approved.",
                    content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/owner/approve")
    @PreAuthorize("hasRole('OWNER')")
    public InspectionResponse ownerApprove(@AuthenticationPrincipal JwtPrincipal principal,
                                         @PathVariable Long id) {
        return toResponse(inspectionService.approveByOwner(principal.userId(), id));
    }

    @Operation(summary = "Decline a pending inspection request (owner)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request declined; slot freed.",
                    content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/owner/decline")
    @PreAuthorize("hasRole('OWNER')")
    public InspectionResponse ownerDecline(@AuthenticationPrincipal JwtPrincipal principal,
                                           @PathVariable Long id) {
        return toResponse(inspectionService.declineByOwner(principal.userId(), id));
    }

    @Operation(summary = "Reschedule an approved inspection to another slot (assigned agent)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot updated; still APPROVED.",
                    content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/agent/reschedule")
    @PreAuthorize("hasRole('AGENT')")
    public InspectionResponse agentReschedule(@AuthenticationPrincipal JwtPrincipal principal,
                                              @PathVariable Long id,
                                              @Valid @RequestBody AgentRescheduleSlotRequest body) {
        return toResponse(inspectionService.rescheduleApprovedByAgent(principal.userId(), id, body.slotId()));
    }

    @Operation(summary = "Set logistics notes on an approved inspection (assigned agent)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extras updated.",
                    content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}/agent/extras")
    @PreAuthorize("hasRole('AGENT')")
    public InspectionResponse agentExtras(@AuthenticationPrincipal JwtPrincipal principal,
                                          @PathVariable Long id,
                                          @Valid @RequestBody AgentExtrasUpdateRequest body) {
        return toResponse(inspectionService.patchAgentExtras(principal.userId(), id, body.extras()));
    }

    @Operation(summary = "Mark an approved inspection as completed (assigned agent)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marked completed.",
                    content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/agent/complete")
    @PreAuthorize("hasRole('AGENT')")
    public InspectionResponse agentComplete(@AuthenticationPrincipal JwtPrincipal principal,
                                            @PathVariable Long id) {
        return toResponse(inspectionService.markCompletedByAgent(principal.userId(), id));
    }

    @Operation(summary = "Mark an approved inspection as no-show (owner or assigned agent)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marked no-show.",
                    content = @Content(schema = @Schema(implementation = InspectionResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/mark-no-show")
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT')")
    public InspectionResponse markNoShow(@AuthenticationPrincipal JwtPrincipal principal,
                                         @PathVariable Long id) {
        return toResponse(inspectionService.markNoShow(principal.userId(), id));
    }

    private InspectionResponse toResponse(InspectionRequest r) {
        return new InspectionResponse(r.getId(), r.getSlotId(), r.getApplicantId(),
                r.getStatus(), r.getNotes(), r.getAgentExtras(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
