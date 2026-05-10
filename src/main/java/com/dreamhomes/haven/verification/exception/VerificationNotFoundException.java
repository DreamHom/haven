package com.dreamhomes.haven.verification.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.verification.model.Verification;

public class VerificationNotFoundException extends DomainException {

    public VerificationNotFoundException(Long verificationId) {
        super(HttpStatus.NOT_FOUND, "Verification " + verificationId + " was not found");
    }
}
