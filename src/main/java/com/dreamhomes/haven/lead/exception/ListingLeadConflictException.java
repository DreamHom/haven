package com.dreamhomes.haven.lead.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingLeadConflictException extends DomainException {

    public ListingLeadConflictException() {
        super(HttpStatus.CONFLICT, "You have already expressed interest on this listing");
    }
}
