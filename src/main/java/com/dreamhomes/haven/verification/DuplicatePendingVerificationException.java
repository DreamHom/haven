package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * The submitter already has a PENDING row of this type for the same target. We surface
 * 409 Conflict rather than letting the partial unique index bubble a generic 23505 —
 * the client can retry once they know the previous submission needs an admin decision.
 */
public class DuplicatePendingVerificationException extends DomainException {

    public DuplicatePendingVerificationException(VerificationType type) {
        super(HttpStatus.CONFLICT,
                "A pending " + type + " verification already exists for this target");
    }
}
