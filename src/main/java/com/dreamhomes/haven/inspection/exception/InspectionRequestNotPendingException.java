package com.dreamhomes.haven.inspection.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Cancel only applies to PENDING requests. Once the owner has acted (APPROVED or
 * DECLINED), the applicant can't unwind that — that's a renegotiation, not a cancel.
 */
public class InspectionRequestNotPendingException extends DomainException {

    public InspectionRequestNotPendingException(Long id) {
        super(HttpStatus.CONFLICT, "Inspection request " + id + " is not PENDING and cannot be cancelled");
    }
}
