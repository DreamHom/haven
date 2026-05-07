package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingNotFoundException extends DomainException {

    public ListingNotFoundException(Long listingId) {
        super(HttpStatus.NOT_FOUND, "Listing " + listingId + " was not found");
    }
}
