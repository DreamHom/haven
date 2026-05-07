package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/** Caller authenticated as an OWNER, but the target property/listing belongs to a different user. */
public class NotPropertyOwnerException extends DomainException {

    public NotPropertyOwnerException() {
        super(HttpStatus.FORBIDDEN, "You don't own this resource");
    }
}
