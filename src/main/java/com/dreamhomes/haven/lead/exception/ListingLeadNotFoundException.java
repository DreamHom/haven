package com.dreamhomes.haven.lead.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingLeadNotFoundException extends DomainException {

    public ListingLeadNotFoundException(Long leadId) {
        super(HttpStatus.NOT_FOUND, "Lead " + leadId + " was not found");
    }
}
