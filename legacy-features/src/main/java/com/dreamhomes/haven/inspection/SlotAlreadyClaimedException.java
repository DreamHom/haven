package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an applicant tries to request a slot that already has an active
 * (PENDING or APPROVED) inspection request. The DB partial unique index rejects
 * the insert; the service translates that to this 409 so the API stays predictable.
 */
public class SlotAlreadyClaimedException extends DomainException {

    public SlotAlreadyClaimedException() {
        super(HttpStatus.CONFLICT, "This slot has already been claimed");
    }
}
