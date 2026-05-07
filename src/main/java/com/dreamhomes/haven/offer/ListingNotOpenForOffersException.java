package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/** Applicant tried to submit an offer on a listing that isn't currently LIVE. */
public class ListingNotOpenForOffersException extends DomainException {

    public ListingNotOpenForOffersException() {
        super(HttpStatus.BAD_REQUEST, "Listing is not currently accepting offers");
    }
}
