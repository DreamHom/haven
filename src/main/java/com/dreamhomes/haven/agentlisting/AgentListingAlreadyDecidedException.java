package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

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
