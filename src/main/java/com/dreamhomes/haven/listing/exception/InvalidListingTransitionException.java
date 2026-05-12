package com.dreamhomes.haven.listing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.listing.model.ListingStatus;

/** Caller asked for a status transition that we don't allow (e.g. CLOSED → LIVE). */
public class InvalidListingTransitionException extends DomainException {

    public InvalidListingTransitionException(ListingStatus from, ListingStatus to) {
        // 409 Conflict (not 400) — the input is well-formed; the conflict is with the
        // listing's current state. The spec documents this as 409 and persona audit
        // (Amaka) caught the 400 vs 409 drift.
        super(HttpStatus.CONFLICT,
                "Cannot transition listing from " + from + " to " + to);
    }
}
