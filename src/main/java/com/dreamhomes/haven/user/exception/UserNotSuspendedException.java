package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.user.model.User;

/**
 * Admin tried to reactivate a user that isn't currently suspended. 409 keeps the audit
 * story honest — re-issuing reactivate as a no-op would silently bury the stale UI.
 */
public class UserNotSuspendedException extends DomainException {

    public UserNotSuspendedException(Long userId) {
        super(HttpStatus.CONFLICT, "User " + userId + " is not currently suspended");
    }
}
