package com.dreamhomes.haven.agentlisting.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller is authenticated as an AGENT but isn't the targeted agent on this assignment.
 * 403 with a generic message — don't leak which agent owns the row.
 */
public class NotTargetedAgentException extends DomainException {

    public NotTargetedAgentException() {
        super(HttpStatus.FORBIDDEN, "You aren't the agent on this assignment");
    }
}
