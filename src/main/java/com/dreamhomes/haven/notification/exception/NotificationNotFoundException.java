package com.dreamhomes.haven.notification.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.notification.model.Notification;

public class NotificationNotFoundException extends DomainException {

    public NotificationNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Notification " + id + " was not found");
    }
}
