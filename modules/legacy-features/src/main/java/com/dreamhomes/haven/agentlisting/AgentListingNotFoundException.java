package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentListingNotFoundException extends DomainException {

    public AgentListingNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Agent assignment " + id + " was not found");
    }
}
