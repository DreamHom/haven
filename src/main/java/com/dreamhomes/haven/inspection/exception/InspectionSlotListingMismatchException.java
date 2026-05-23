package com.dreamhomes.haven.inspection.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class InspectionSlotListingMismatchException extends DomainException {

    public InspectionSlotListingMismatchException() {
        super(HttpStatus.BAD_REQUEST, "The target slot belongs to a different listing");
    }
}
