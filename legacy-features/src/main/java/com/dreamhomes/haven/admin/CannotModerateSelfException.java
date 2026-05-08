package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Admin tried to suspend / reactivate / takedown their own account. Disallowed to prevent
 * a single admin from locking themselves out — moderation of admin accounts must come
 * from another admin.
 */
public class CannotModerateSelfException extends DomainException {

    public CannotModerateSelfException() {
        super(HttpStatus.FORBIDDEN, "Admins cannot moderate their own account");
    }
}
