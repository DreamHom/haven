package com.dreamhomes.haven.verification.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * The submitter referenced a {@code livenessCheckId} that either does not exist or
 * belongs to a different user. Both cases collapse to 403 to avoid leaking which one
 * it was — same posture as {@link VerificationRoleMismatchException}.
 */
public class LivenessCheckNotFoundException extends DomainException {

    public LivenessCheckNotFoundException(Long livenessCheckId) {
        super(HttpStatus.FORBIDDEN,
                "Liveness check " + livenessCheckId + " was not found for this user");
    }
}
