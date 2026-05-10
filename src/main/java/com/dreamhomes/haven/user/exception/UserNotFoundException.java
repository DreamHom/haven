package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.user.model.User;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "User " + userId + " was not found");
    }
}
