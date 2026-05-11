package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.user.model.User;

public class UserAlreadySuspendedException extends DomainException {

    public UserAlreadySuspendedException(Long userId) {
        super(HttpStatus.CONFLICT, "User " + userId + " is already suspended");
    }
}
