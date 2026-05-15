package com.dreamhomes.haven.listingreport.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingReportAlreadyResolvedException extends DomainException {

    public ListingReportAlreadyResolvedException(Long id) {
        super(HttpStatus.CONFLICT, "Listing report " + id + " is not PENDING and cannot be resolved or dismissed");
    }
}
