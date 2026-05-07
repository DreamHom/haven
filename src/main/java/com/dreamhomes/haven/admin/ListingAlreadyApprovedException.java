package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Admin tried to approve a listing that already carries the verified-listing badge.
 * Re-approval is meaningless — surface 409 so the admin UI can refresh state instead
 * of silently pretending the action succeeded.
 */
public class ListingAlreadyApprovedException extends DomainException {

    public ListingAlreadyApprovedException(Long listingId) {
        super(HttpStatus.CONFLICT, "Listing " + listingId + " is already approved");
    }
}
