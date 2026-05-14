package com.dreamhomes.haven.lead.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ListingNotAcceptingLeadsException extends DomainException {

    public ListingNotAcceptingLeadsException() {
        super(HttpStatus.BAD_REQUEST, "This listing is not accepting interest submissions");
    }
}
