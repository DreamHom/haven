package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.offer.dto.CounterOfferRequest;
import com.dreamhomes.haven.offer.dto.OfferResponse;
import com.dreamhomes.haven.offer.dto.RespondToOfferRequest;
import com.dreamhomes.haven.offer.dto.SubmitOfferCommand;
import com.dreamhomes.haven.offer.dto.SubmitOfferRequest;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
@Tag(name = "Offers")
public class OfferController {

    private final OfferService offerService;
    private final OfferMapper offerMapper;

    @Operation(
            summary = "Submit an offer on a listing",
            description = """
                    Records a `PENDING` offer at the price + terms the applicant proposes \
                    and fires an `OFFER_SUBMITTED` notification (via outbox + Kafka) to the \
                    listing's owner. The offer is the root of a counter-offer chain — \
                    counters from the owner attach as children via `parentOfferId`.

                    **Preconditions**
                    - Listing is in `OPEN` state.
                    - Caller is not the listing owner (you can't offer on your own listing).
                    - Caller has no other PENDING offer on this listing.

                    **Side effect**: outbox row + Kafka event + owner notification (and \
                    assigned-agent notification if any).

                    **Role gate**: `APPLICANT`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Offer created in PENDING.",
                    content = @Content(
                            schema = @Schema(implementation = OfferResponse.class),
                            examples = @ExampleObject(name = "PendingOffer", value = """
                                    { "id": 42, "listingId": 17, "applicantId": 89, "ownerId": 7,
                                      "amount": 7500000, "currency": "NGN",
                                      "message": "Could close this week if accepted.",
                                      "status": "PENDING", "parentOfferId": null,
                                      "proposedByUserId": 89,
                                      "createdAt": "2026-05-10T08:30:00Z",
                                      "updatedAt": "2026-05-10T08:30:00Z" }
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
    public OfferResponse submit(@AuthenticationPrincipal JwtPrincipal principal,
                                @Valid @RequestBody SubmitOfferRequest request) {
        return offerMapper.toResponse(offerService.submit(principal.userId(), new SubmitOfferCommand(
                request.listingId(), request.amount(), request.currency(), request.message(), request.intent())));
    }

    @Operation(
            summary = "List my offers",
            description = """
                    Returns every offer where the caller is either the applicant who
                    submitted it or the owner who received it, newest first. The persona audit
                    (Temi, Biodun) flagged this as the single biggest "lost the thread" gap:
                    a missed notification meant a permanently lost deal, since there was no
                    other path back to the offer's ID.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of the caller's offers."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine")
    public Page<OfferResponse> listMine(@AuthenticationPrincipal JwtPrincipal principal,
                                        @PageableDefault(size = 20) Pageable pageable) {
        return offerService.listMine(principal.userId(), pageable).map(offerMapper::toResponse);
    }

    @Operation(
            summary = "Respond to an offer (accept or decline)",
            description = """
                    Transitions a PENDING offer to `ACCEPTED` or `DECLINED`. Either side of \
                    the negotiation can call this against an offer the **other** side proposed \
                    (the service rejects "responding to your own proposal" with 403).

                    **Critical rule**: when an offer is `ACCEPTED`, every other PENDING offer \
                    on the same listing is locked from acceptance — the listing has a single \
                    accepted deal at most. The state-machine + the listing's `@Version` enforces \
                    this; concurrent accepts of two different offers on the same listing — only \
                    one wins, the other gets 409.

                    **State machine**: `PENDING` → `ACCEPTED` | `DECLINED` | `COUNTERED`. \
                    Re-acting on a terminal state returns 409.

                    **Role gate**: OWNER or APPLICANT — service does fine-grained "you must \
                    not be the proposer" check.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Offer transitioned. Response shows the new status.",
                    content = @Content(
                            schema = @Schema(implementation = OfferResponse.class),
                            examples = @ExampleObject(name = "AcceptedOffer", value = """
                                    { "id": 42, "listingId": 17, "applicantId": 89, "ownerId": 7,
                                      "amount": 7500000, "currency": "NGN",
                                      "status": "ACCEPTED", "parentOfferId": null,
                                      "proposedByUserId": 89,
                                      "createdAt": "2026-05-10T08:30:00Z",
                                      "updatedAt": "2026-05-11T09:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'APPLICANT', 'AGENT')")
    public OfferResponse respond(@AuthenticationPrincipal JwtPrincipal principal,
                                 @Parameter(description = "Offer ID to respond to.", example = "42")
                                 @PathVariable Long id,
                                 @Valid @RequestBody RespondToOfferRequest request) {
        return offerMapper.toResponse(offerService.respond(
                principal.userId(), id, request.status().toOfferStatus(), request.reason()));
    }

    @Operation(
            summary = "Withdraw my offer",
            description = """
                    Applicant withdraws a PENDING offer they submitted. Frees the listing to
                    receive a fresh offer from the same applicant. Returns 409 if the offer
                    is no longer PENDING (owner already accepted/declined/countered). 403 if
                    the caller is not the applicant on this offer.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Offer withdrawn."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('APPLICANT')")
    public void withdraw(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long id) {
        offerService.withdraw(principal.userId(), id);
    }

    @Operation(
            summary = "Counter an offer",
            description = """
                    Marks the parent offer as `COUNTERED` and creates a child offer in \
                    `PENDING` carrying the new amount + message. The chain links via \
                    `parentOfferId` — `GET` traversal of a single offer's lineage shows the \
                    full negotiation arc.

                    **Side effect**: notification fires to the other party (the original \
                    proposer of the parent offer).

                    **Role gate**: OWNER or APPLICANT — service does the "you must not be \
                    the proposer of the parent" check.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Counter-offer created; parent moved to COUNTERED.",
                    content = @Content(
                            schema = @Schema(implementation = OfferResponse.class),
                            examples = @ExampleObject(name = "CounteredOffer", value = """
                                    { "id": 43, "listingId": 17, "applicantId": 89, "ownerId": 7,
                                      "amount": 8000000, "currency": "NGN",
                                      "message": "Closer to ask. Could meet at 8m.",
                                      "status": "PENDING", "parentOfferId": 42,
                                      "proposedByUserId": 7,
                                      "createdAt": "2026-05-11T10:00:00Z",
                                      "updatedAt": "2026-05-11T10:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/counter")
    @PreAuthorize("hasAnyRole('OWNER', 'APPLICANT', 'AGENT')")
    public OfferResponse counter(@AuthenticationPrincipal JwtPrincipal principal,
                                 @Parameter(description = "Parent offer ID being countered.", example = "42")
                                 @PathVariable Long id,
                                 @Valid @RequestBody CounterOfferRequest request) {
        return offerMapper.toResponse(offerService.counter(
                principal.userId(), id, request.amount(), request.message()));
    }
}
