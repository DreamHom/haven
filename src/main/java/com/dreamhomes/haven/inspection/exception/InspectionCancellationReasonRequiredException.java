package com.dreamhomes.haven.inspection.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when {@code cancelByEitherParty} is called without a reason or with a blank
 * reason. The reason is REQUIRED so the other party can be told what happened.
 */
public class InspectionCancellationReasonRequiredException extends DomainException {

    public InspectionCancellationReasonRequiredException() {
        super(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
    }
}
