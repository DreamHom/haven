package com.dreamhomes.haven.inspection.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class InspectionRequestNotFoundException extends DomainException {

    public InspectionRequestNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Inspection request " + id + " was not found");
    }
}
