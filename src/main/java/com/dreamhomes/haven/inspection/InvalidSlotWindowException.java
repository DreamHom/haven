package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidSlotWindowException extends DomainException {

    public InvalidSlotWindowException() {
        super(HttpStatus.BAD_REQUEST, "Slot ends_at must be after starts_at");
    }
}
