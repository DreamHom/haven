package com.dreamhomes.haven.listing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.listing.model.ListingStatus;

/** Caller asked for a status transition that we don't allow (e.g. CLOSED → LIVE). */
public class InvalidListingTransitionException extends DomainException {

    public InvalidListingTransitionException(ListingStatus from, ListingStatus to) {
        super(HttpStatus.BAD_REQUEST,
                "Cannot transition listing from " + from + " to " + to);
    }
}
