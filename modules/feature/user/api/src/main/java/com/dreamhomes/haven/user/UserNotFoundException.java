package com.dreamhomes.haven.user;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "User " + userId + " was not found");
    }
}
