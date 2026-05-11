package com.dreamhomes.haven.agentlisting.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Owner tried to invite a user who either doesn't exist or doesn't have role=AGENT.
 * 404 — same response shape regardless of which it is, so we don't leak whether the user
 * exists.
 */
public class AgentNotFoundOrWrongRoleException extends DomainException {

    public AgentNotFoundOrWrongRoleException(Long agentId) {
        super(HttpStatus.NOT_FOUND, "Agent " + agentId + " was not found");
    }
}
