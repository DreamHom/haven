package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.agentlisting.dto.AgentListingResponse;
import com.dreamhomes.haven.agentlisting.dto.DeclineAssignmentRequest;
import com.dreamhomes.haven.agentlisting.dto.RequestAgentAssignmentRequest;
import com.dreamhomes.haven.agentlisting.dto.RevokeAssignmentRequest;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
import com.dreamhomes.haven.auth.JwtPrincipal;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for the agent–listing assignment handshake.
 *
 * <p>Three role-gated POSTs and a paginated GET for the caller's own assignments. The
 * controller delegates authorisation to the service for the multi-condition checks
 * (the simple role gate goes through {@code @PreAuthorize}; the "is this caller the
 * targeted agent / listing owner / admin" check lives in the service alongside the
 * state-machine logic).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Agent assignments")
@org.springframework.validation.annotation.Validated
public class AgentListingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AgentListingService agentListingService;
    private final AgentListingMapper agentListingMapper;

    @Operation(
            summary = "Invite an agent to manage a listing",
            description = """
                    Records an `AgentListing` row in `REQUESTED` status. The targeted agent \
                    receives a notification and can `accept` or `decline`. Only one PENDING \
                    invite per `(listing, agent)` pair (DB-level partial unique index).

                    **Constraints**
                    - Target user must exist and have role `AGENT` (else 400 / 404).
                    - Listing must not already have an ACCEPTED agent — revoke first (409).
                    - Caller must be the listing's owner.

                    **Side effect**: notification fired to the targeted agent.

                    **Role gate**: `OWNER`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Invite created in REQUESTED.",
                    content = @Content(
                            schema = @Schema(implementation = AgentListingResponse.class),
                            examples = @ExampleObject(name = "RequestedInvite", value = """
                                    { "id": 51, "listingId": 17, "agentUserId": 23,
                                      "requestedByOwnerId": 7, "status": "REQUESTED",
                                      "decisionReason": null,
                                      "requestedAt": "2026-05-10T14:00:00Z",
                                      "decidedAt": null }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/listings/{listingId}/agent-assignment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public AgentListingResponse request(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing the agent is being invited to manage.", example = "17")
            @PathVariable Long listingId,
            @Valid @RequestBody RequestAgentAssignmentRequest request) {
        return agentListingMapper.toResponse(
                agentListingService.request(principal.userId(), listingId, request.agentId()));
    }

    @Operation(
            summary = "Invite agents to many listings in one call",
            description = """
                    Bulk variant of {@code POST /api/listings/{listingId}/agent-assignment}.
                    Persona audit (Biodun): an owner with 60 freshly-listed units shouldn't
                    have to fire 60 separate invite calls when they've already picked one
                    or two agents to manage the whole tower. Each row pairs a listing with
                    the agent to invite. Owner-of-listing + agent-role checks run per row.
                    Whole batch is one transaction.

                    **Limits**: at most 100 invitations per call.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All invitations created."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/agent-listings/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public java.util.List<AgentListingResponse> requestBulk(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody @jakarta.validation.constraints.Size(min = 1, max = 100)
            java.util.List<com.dreamhomes.haven.agentlisting.dto.BulkAssignmentRequest> requests) {
        java.util.List<AgentListingResponse> out = new java.util.ArrayList<>(requests.size());
        for (var r : requests) {
            out.add(agentListingMapper.toResponse(
                    agentListingService.request(principal.userId(), r.listingId(), r.agentId())));
        }
        return out;
    }

    @Operation(
            summary = "Accept an agent-assignment invite",
            description = """
                    Transitions the `AgentListing` from `REQUESTED` to `ACCEPTED`. The agent \
                    is now authorised on the listing's slot + inspection-response endpoints \
                    (alongside the owner). Notification fires to the owner.

                    **Authorisation**: caller must be the targeted agent (the one named in \
                    `agentUserId`). Other agents accepting someone else's invite → 403.

                    **State machine**: only `REQUESTED` rows can be accepted. Re-accepting \
                    or accepting a previously declined invite returns 409.

                    **Role gate**: `AGENT`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Invite accepted.",
                    content = @Content(schema = @Schema(implementation = AgentListingResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/agent-listings/{id}/accept")
    @PreAuthorize("hasRole('AGENT')")
    public AgentListingResponse accept(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "AgentListing ID.", example = "51")
            @PathVariable Long id) {
        return agentListingMapper.toResponse(
                agentListingService.respond(principal.userId(), id, AgentListingStatus.ACCEPTED, null));
    }

    @Operation(
            summary = "Decline an agent-assignment invite",
            description = """
                    Transitions the `AgentListing` from `REQUESTED` to `DECLINED` with the \
                    agent's reason. Owner gets a notification and is free to invite a \
                    different agent.

                    **Role gate**: `AGENT`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Invite declined.",
                    content = @Content(schema = @Schema(implementation = AgentListingResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/agent-listings/{id}/decline")
    @PreAuthorize("hasRole('AGENT')")
    public AgentListingResponse decline(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "AgentListing ID.", example = "51")
            @PathVariable Long id,
            @Valid @RequestBody DeclineAssignmentRequest request) {
        return agentListingMapper.toResponse(
                agentListingService.respond(principal.userId(), id, AgentListingStatus.DECLINED, request.reason()));
    }

    @Operation(
            summary = "Revoke an active agent assignment",
            description = """
                    Transitions an `ACCEPTED` `AgentListing` to `REVOKED`. The agent loses \
                    authorisation on the listing's privileged endpoints immediately and \
                    receives a notification. Owner is free to invite a different agent.

                    **Authorisation** (service-enforced): listing's owner OR an admin can \
                    revoke. Anyone else gets 403.

                    **State machine**: only `ACCEPTED` rows can be revoked. Trying to revoke \
                    a REQUESTED / DECLINED / already-REVOKED row returns 409.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Assignment revoked.",
                    content = @Content(schema = @Schema(implementation = AgentListingResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/agent-listings/{id}/revoke")
    public AgentListingResponse revoke(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "AgentListing ID.", example = "51")
            @PathVariable Long id,
            @Valid @RequestBody RevokeAssignmentRequest request) {
        return agentListingMapper.toResponse(
                agentListingService.revoke(principal.userId(), principal.role(), id, request.reason()));
    }

    @Operation(
            summary = "List the calling user's agent assignments",
            description = """
                    Paginated list of `AgentListing` rows where the caller is either the \
                    targeted agent (when called by an AGENT) or the requesting owner (when \
                    called by an OWNER). The role-aware split lets one endpoint serve both \
                    sides of the handshake.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated assignments scoped to the caller.",
                    content = @Content(
                            examples = @ExampleObject(name = "OneAcceptedTwoRequested", value = """
                                    { "content": [
                                        { "id": 51, "listingId": 17, "agentUserId": 23,
                                          "requestedByOwnerId": 7, "status": "ACCEPTED",
                                          "decisionReason": null,
                                          "requestedAt": "2026-05-09T14:00:00Z",
                                          "decidedAt":   "2026-05-09T15:00:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 } }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/agent-listings/mine")
    public Page<AgentListingResponse> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @io.swagger.v3.oas.annotations.Parameter(description = "Filter by status: REQUESTED, ACCEPTED, DECLINED, REVOKED.")
            @RequestParam(required = false) com.dreamhomes.haven.agentlisting.model.AgentListingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return agentListingService.listMine(principal.userId(), principal.role(), status, pageable)
                .map(agentListingMapper::toResponse);
    }

}
