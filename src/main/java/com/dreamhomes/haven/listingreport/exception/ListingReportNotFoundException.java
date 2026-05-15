package com.dreamhomes.haven.listingreport.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingReportNotFoundException extends DomainException {

    public ListingReportNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Listing report " + id + " was not found");
    }
}
