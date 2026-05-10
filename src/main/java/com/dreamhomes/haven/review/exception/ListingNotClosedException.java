package com.dreamhomes.haven.review.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.listing.model.Listing;

/**
 * Caller tried to post a review on a listing whose status is not yet CLOSED. Reviews
 * are post-deal trust signals — they only make sense after the deal is done.
 */
public class ListingNotClosedException extends DomainException {

    public ListingNotClosedException(Long listingId) {
        super(HttpStatus.CONFLICT, "Listing " + listingId + " is not closed; reviews open after the deal is done");
    }
}
