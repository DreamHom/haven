package com.dreamhomes.haven.agentlisting.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
/**
 * Agent tried to accept / decline an assignment whose status is no longer REQUESTED —
 * either they've already responded, or the owner revoked the invite.
 */
public class AgentListingAlreadyDecidedException extends DomainException {

    public AgentListingAlreadyDecidedException(Long id, AgentListingStatus current) {
        super(HttpStatus.CONFLICT,
                "Assignment " + id + " is already " + current);
    }
}
