package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Owner already has a REQUESTED row for this listing — the partial UQ from V13 enforces
 * one outstanding invite at a time. To invite a different agent, the owner must revoke
 * the pending row first.
 */
public class ListingAlreadyHasPendingInviteException extends DomainException {

    public ListingAlreadyHasPendingInviteException(Long listingId) {
        super(HttpStatus.CONFLICT,
                "Listing " + listingId + " already has a pending agent invite");
    }
}
