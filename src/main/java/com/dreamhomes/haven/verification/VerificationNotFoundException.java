package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class VerificationNotFoundException extends DomainException {

    public VerificationNotFoundException(Long verificationId) {
        super(HttpStatus.NOT_FOUND, "Verification " + verificationId + " was not found");
    }
}
