package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends DomainException {

    public NotificationNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Notification " + id + " was not found");
    }
}
