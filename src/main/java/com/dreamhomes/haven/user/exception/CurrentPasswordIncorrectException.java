package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class CurrentPasswordIncorrectException extends DomainException {

    public CurrentPasswordIncorrectException() {
        super(HttpStatus.FORBIDDEN, "Current password is incorrect");
    }
}
