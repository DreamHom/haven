package com.dreamhomes.haven.verification.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller passed a {@code livenessCheckId} that has already been consumed by a previous
 * verification submission. One liveness check = one verification submit — replays
 * surface as 409 so the client knows to run a fresh check.
 */
public class LivenessCheckAlreadyConsumedException extends DomainException {

    public LivenessCheckAlreadyConsumedException(Long livenessCheckId) {
        super(HttpStatus.CONFLICT,
                "Liveness check " + livenessCheckId + " has already been consumed");
    }
}
