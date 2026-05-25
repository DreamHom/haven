package com.dreamhomes.haven.inspection.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown by {@code cancelByEitherParty} when the target request is not in a cancellable
 * state (i.e. not PENDING or APPROVED). 409 — distinct from the legacy
 * {@code InspectionRequestNotPendingException} so the new flow can stamp a clearer
 * message.
 */
public class InspectionRequestNotCancellableException extends DomainException {

    public InspectionRequestNotCancellableException(Long id) {
        super(HttpStatus.CONFLICT,
                "Inspection request " + id + " is not in a cancellable state (only PENDING or APPROVED can be cancelled)");
    }
}
