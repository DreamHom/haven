package com.dreamhomes.haven.listing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.listing.model.Listing;

public class ListingNotFoundException extends DomainException {

    public ListingNotFoundException(Long listingId) {
        super(HttpStatus.NOT_FOUND, "Listing " + listingId + " was not found");
    }
}
