package com.dreamhomes.haven.verification.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;

/**
 * Admin tried to approve/reject a verification that's already been decided. APPROVED
 * and REJECTED are terminal states — the submitter must re-submit if they want a fresh
 * decision.
 */
public class VerificationAlreadyDecidedException extends DomainException {

    public VerificationAlreadyDecidedException(Long verificationId, VerificationStatus status) {
        super(HttpStatus.CONFLICT,
                "Verification " + verificationId + " is already " + status);
    }
}
