package com.dreamhomes.haven.notification.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller tried to read or mark-read a notification that isn't theirs. 403 with a
 * generic message so we don't leak whether the notification exists.
 */
public class NotMyNotificationException extends DomainException {

    public NotMyNotificationException() {
        super(HttpStatus.FORBIDDEN, "This notification doesn't belong to you");
    }
}
