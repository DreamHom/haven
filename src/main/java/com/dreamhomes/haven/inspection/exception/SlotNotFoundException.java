package com.dreamhomes.haven.inspection.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class SlotNotFoundException extends DomainException {

    public SlotNotFoundException(Long slotId) {
        super(HttpStatus.NOT_FOUND, "Inspection slot " + slotId + " was not found");
    }
}
