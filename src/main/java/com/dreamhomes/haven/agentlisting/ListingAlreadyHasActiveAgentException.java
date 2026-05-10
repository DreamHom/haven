package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Owner tried to invite an agent while another agent is already actively managing this
 * listing. The owner must revoke the active assignment first.
 */
public class ListingAlreadyHasActiveAgentException extends DomainException {

    public ListingAlreadyHasActiveAgentException(Long listingId) {
        super(HttpStatus.CONFLICT,
                "Listing " + listingId + " already has an active agent");
    }
}
