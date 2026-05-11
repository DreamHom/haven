package com.dreamhomes.haven.agentlisting.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.listing.model.Listing;

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
