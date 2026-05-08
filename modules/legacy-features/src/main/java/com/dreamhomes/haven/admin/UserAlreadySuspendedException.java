package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class UserAlreadySuspendedException extends DomainException {

    public UserAlreadySuspendedException(Long userId) {
        super(HttpStatus.CONFLICT, "User " + userId + " is already suspended");
    }
}
