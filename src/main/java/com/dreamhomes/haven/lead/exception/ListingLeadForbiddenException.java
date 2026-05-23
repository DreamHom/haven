package com.dreamhomes.haven.lead.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingLeadForbiddenException extends DomainException {

    public ListingLeadForbiddenException() {
        super(HttpStatus.FORBIDDEN, "You cannot submit interest on your own listing");
    }
}
