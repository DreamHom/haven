package com.dreamhomes.haven.listing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentCannotEditListingFieldsException extends DomainException {

    public AgentCannotEditListingFieldsException() {
        super(HttpStatus.FORBIDDEN,
                "Assigned agents cannot change asking price or listing status — coordinate with the owner");
    }
}
