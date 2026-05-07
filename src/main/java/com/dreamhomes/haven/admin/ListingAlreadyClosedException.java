package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Admin tried to take down a listing that is already CLOSED. Idempotency would mask
 * a stale UI; surfacing 409 forces the client to refresh.
 */
public class ListingAlreadyClosedException extends DomainException {

    public ListingAlreadyClosedException(Long listingId) {
        super(HttpStatus.CONFLICT, "Listing " + listingId + " is already closed");
    }
}
