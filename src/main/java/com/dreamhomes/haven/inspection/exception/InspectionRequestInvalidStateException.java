package com.dreamhomes.haven.inspection.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class InspectionRequestInvalidStateException extends DomainException {

    public InspectionRequestInvalidStateException(Long id, String message) {
        super(HttpStatus.CONFLICT, "Inspection request " + id + ": " + message);
    }
}
