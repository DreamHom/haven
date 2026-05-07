package com.dreamhomes.haven.review;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller tried to post a review on a listing whose status is not yet CLOSED. Reviews
 * are post-deal trust signals — they only make sense after the deal is done.
 */
public class ListingNotClosedException extends DomainException {

    public ListingNotClosedException(Long listingId) {
        super(HttpStatus.CONFLICT, "Listing " + listingId + " is not closed; reviews open after the deal is done");
    }
}
