package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller is none of: the listing's owner, the assigned agent, or an admin. Either party
 * can revoke an active assignment, but a random third party can't.
 */
public class NotAuthorisedToRevokeException extends DomainException {

    public NotAuthorisedToRevokeException() {
        super(HttpStatus.FORBIDDEN, "You aren't authorised to revoke this assignment");
    }
}
