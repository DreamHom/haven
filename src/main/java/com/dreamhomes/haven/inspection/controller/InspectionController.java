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

    @Operation(
            summary = "Reschedule an APPROVED inspection to another slot (assigned agent)",
            description = """
                    Moves the inspection from its current slot onto a new slot on the SAME \
                    listing. The request stays APPROVED — only the slot pointer changes.

                    **State guard**: must be `APPROVED`. PENDING / DECLINED / CANCELLED / \
                    COMPLETED / NO_SHOW → 409. Trying to move to the SAME slot is a no-op \
                    (returns 200 with the unchanged row).

                    **Auth**: caller must be the active assigned agent on the listing \
                    (`AGENT` role + `agent_listings.status='ACCEPTED'`). Owner cannot use \
                    this endpoint — owners reschedule by declining + asking the applicant \
                    to re-book.

                    **Side effects**: writes a new slot pointer; no notification fan-out \
                    today (a follow-up will fire an `INSPECTION_RESCHEDULED` event). The \
                    partial UQ on `inspection_requests(slot_id) WHERE status IN \
                    ('PENDING','APPROVED')` enforces "one active request per slot" at the \
                    DB level — a race against another applicant losing returns 409.

                    **Error map**:
                    - `400` body fails validation (e.g. missing `slotId`).
                    - `403` caller is not the active assigned agent.
                    - `404` request id or new slot id does not exist.
                    - `409` request is not APPROVED, new slot is on a different listing, \
                      or the new slot is already claimed by another active request.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot updated; still APPROVED.",
                    content = @Content(
                            schema = @Schema(implementation = InspectionResponse.class),
                            examples = @ExampleObject(name = "Rescheduled", value = """
                                    { "id": 33, "slotId": 60, "applicantId": 100,
                                      "status": "APPROVED", "notes": "Coming with my husband.",
                                      "agentExtras": null,
                                      "createdAt": "2026-05-10T12:00:00Z",
                                      "updatedAt": "2026-05-11T08:30:00Z" }
                                    """))),
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
                                              @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                      required = true,
                                                      content = @Content(
                                                              schema = @Schema(implementation = AgentRescheduleSlotRequest.class),
                                                              examples = @ExampleObject(name = "Reschedule", value = """
                                                                      { "slotId": 60 }
                                                                      """)))
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

    @Operation(
            summary = "Mark an APPROVED inspection as completed (assigned agent)",
            description = """
                    Closes the loop after a successful visit. Status flips to `COMPLETED` \
                    (terminal). UX recommendation: surface this action only after the \
                    slot's end time has passed — completing an inspection before the \
                    booked window has ended is suspicious data.

                    **State guard**: must be `APPROVED`. All other states → 409.

                    **Auth**: caller must be the active assigned agent on the listing.

                    **Side effects**: row goes terminal, the partial UQ on \
                    `inspection_requests(slot_id)` releases the slot. No notification fires \
                    today (a follow-up will add `INSPECTION_COMPLETED` so the applicant \
                    sees the closing receipt and the owner sees agent productivity \
                    summaries).

                    **Error map**:
                    - `403` caller is not the active assigned agent.
                    - `404` request id does not exist.
                    - `409` request is not APPROVED.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marked completed.",
                    content = @Content(
                            schema = @Schema(implementation = InspectionResponse.class),
                            examples = @ExampleObject(name = "Completed", value = """
                                    { "id": 33, "slotId": 12, "applicantId": 100,
                                      "status": "COMPLETED", "notes": "Coming with my husband.",
                                      "agentExtras": "Met at side gate, applicant satisfied.",
                                      "createdAt": "2026-05-10T12:00:00Z",
                                      "updatedAt": "2026-05-12T14:30:00Z" }
                                    """))),
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

    @Operation(
            summary = "Mark an APPROVED inspection as no-show (owner or assigned agent)",
            description = """
                    Records that the applicant didn't show up for their booked slot. Status \
                    flips to `NO_SHOW` (terminal). UX recommendation: surface this action \
                    only after the slot's start time has passed — marking a no-show before \
                    the booking has even started is suspicious.

                    **State guard**: must be `APPROVED`. All other states → 409.

                    **Auth**: caller must be either the listing owner OR the active \
                    assigned agent. The applicant CANNOT mark themselves no-show; that would \
                    be a cancel.

                    **Side effects**: row goes terminal, slot is freed. Future follow-up \
                    may notify the applicant + factor into their reputation aggregate.

                    **Error map**:
                    - `403` caller is neither the listing owner nor the active assigned agent.
                    - `404` request id does not exist.
                    - `409` request is not APPROVED.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marked no-show.",
                    content = @Content(
                            schema = @Schema(implementation = InspectionResponse.class),
                            examples = @ExampleObject(name = "NoShow", value = """
                                    { "id": 33, "slotId": 12, "applicantId": 100,
                                      "status": "NO_SHOW", "notes": null,
                                      "agentExtras": null,
                                      "createdAt": "2026-05-10T12:00:00Z",
                                      "updatedAt": "2026-05-12T14:30:00Z" }
                                    """))),
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

    @Operation(
            summary = "Cancel an inspection with a reason (any participating party)",
            description = """
                    Cancels a PENDING or APPROVED inspection. Any party — the applicant, \
                    the listing owner, OR the active assigned agent — can call. The \
                    `reason` is required and is included on the in-tray notification fired \
                    to the OTHER parties so they understand what happened.

                    Closes Gap C of post-session-tasks Item 7. Before this endpoint, \
                    APPROVED inspections were a one-way street: an applicant emergency \
                    became a forced no-show on their record, an owner emergency became a \
                    ghosted meeting. Now both sides get a graceful exit.

                    **State guard**: must be `PENDING` or `APPROVED`. CANCELLED / DECLINED \
                    / COMPLETED / NO_SHOW → 409.

                    **Auth**: caller must be the applicant, the listing owner, OR the \
                    active assigned agent on the listing.

                    **Reason guard**: `reason` is required (non-blank) and max 200 chars; \
                    body validation fires 400 if missing.

                    **Side effects**: status goes `CANCELLED`, `cancellation_reason` is \
                    persisted on the row, the slot is freed (partial UQ drops out of the \
                    active set), and an `inspection.cancelled.v1` outbox event is written \
                    in the same transaction. The listener fans out an \
                    `INSPECTION_CANCELLED` notification (carrying the reason) to every \
                    party EXCEPT the caller.

                    **Error map**:
                    - `400` reason missing, blank, or > 200 chars.
                    - `401` not authenticated.
                    - `403` caller is none of {applicant, owner, active assigned agent}.
                    - `404` request id does not exist.
                    - `409` request is not in a cancellable state.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled; slot freed and other parties notified.",
                    content = @Content(
                            schema = @Schema(implementation = InspectionResponse.class),
                            examples = @ExampleObject(name = "Cancelled", value = """
                                    { "id": 33, "slotId": 12, "applicantId": 100,
                                      "status": "CANCELLED", "notes": "Coming with my husband.",
                                      "agentExtras": null,
                                      "createdAt": "2026-05-10T12:00:00Z",
                                      "updatedAt": "2026-05-12T09:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/cancel")
    public InspectionResponse cancelWithReason(@AuthenticationPrincipal JwtPrincipal principal,
                                               @PathVariable Long id,
                                               @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                                       required = true,
                                                       content = @Content(
                                                               schema = @Schema(implementation = com.dreamhomes.haven.inspection.dto.CancelInspectionRequest.class),
                                                               examples = @ExampleObject(name = "CancelWithReason", value = """
                                                                       { "reason": "Work emergency, can't make it" }
                                                                       """)))
                                               @Valid @RequestBody com.dreamhomes.haven.inspection.dto.CancelInspectionRequest body) {
        return toResponse(inspectionService.cancelByEitherParty(principal.userId(), id, body.reason()));
    }

    private InspectionResponse toResponse(InspectionRequest r) {
        return new InspectionResponse(r.getId(), r.getSlotId(), r.getApplicantId(),
                r.getStatus(), r.getNotes(), r.getAgentExtras(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
