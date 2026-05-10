package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class OfferController {

    private final OfferService offerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('APPLICANT')")
    public OfferResponse submit(@AuthenticationPrincipal JwtPrincipal principal,
                                @Valid @RequestBody SubmitOfferRequest request) {
        return toResponse(offerService.submit(principal.userId(), new SubmitOfferCommand(
                request.listingId(), request.amount(), request.currency(), request.message())));
    }

    /**
     * Phase 13: respond is now also reachable to applicants (when responding to an
     * owner's counter). Authorisation lives in the service: caller must be a participant
     * AND not the proposer of the row being acted on.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'APPLICANT')")
    public OfferResponse respond(@AuthenticationPrincipal JwtPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody RespondToOfferRequest request) {
        return toResponse(offerService.respond(principal.userId(), id, request.status()));
    }

    /**
     * Phase 13: counter-offer. Caller can be either the owner (countering applicant's
     * pending) or the applicant (countering owner's pending counter). Service does the
     * "must not be proposer" check.
     */
    @PostMapping("/{id}/counter")
    @PreAuthorize("hasAnyRole('OWNER', 'APPLICANT')")
    public OfferResponse counter(@AuthenticationPrincipal JwtPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody CounterOfferRequest request) {
        return toResponse(offerService.counter(
                principal.userId(), id, request.amount(), request.message()));
    }

    static OfferResponse toResponse(Offer o) {
        return new OfferResponse(o.getId(), o.getListingId(), o.getApplicantId(), o.getOwnerId(),
                o.getAmount(), o.getCurrency(), o.getMessage(), o.getStatus(),
                o.getParentOfferId(), o.getProposedByUserId(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
