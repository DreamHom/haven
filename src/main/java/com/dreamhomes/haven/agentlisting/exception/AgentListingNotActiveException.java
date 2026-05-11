package com.dreamhomes.haven.agentlisting.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
/**
 * Caller tried to revoke an assignment that isn't currently ACCEPTED. To withdraw a
 * pending invite use the same revoke endpoint — but the row must be REQUESTED to be
 * cancellable.
 */
public class AgentListingNotActiveException extends DomainException {

    public AgentListingNotActiveException(Long id, AgentListingStatus current) {
        super(HttpStatus.CONFLICT,
                "Assignment " + id + " is " + current + ", not active");
    }
}
