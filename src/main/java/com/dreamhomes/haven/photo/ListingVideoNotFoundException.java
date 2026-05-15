package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingVideoNotFoundException extends DomainException {

    public ListingVideoNotFoundException(Long videoId) {
        super(HttpStatus.NOT_FOUND, "Listing video not found: " + videoId);
    }
}
