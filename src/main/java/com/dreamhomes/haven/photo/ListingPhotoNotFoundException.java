package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingPhotoNotFoundException extends DomainException {

    public ListingPhotoNotFoundException(Long photoId) {
        super(HttpStatus.NOT_FOUND, "Photo " + photoId + " was not found");
    }
}
