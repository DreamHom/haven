package com.dreamhomes.haven.offer.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.listing.model.Listing;

/** Applicant tried to submit an offer on a listing that isn't currently LIVE. */
public class ListingNotOpenForOffersException extends DomainException {

    public ListingNotOpenForOffersException() {
        super(HttpStatus.BAD_REQUEST, "Listing is not currently accepting offers");
    }
}
