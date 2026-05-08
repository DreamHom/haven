package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.common.DomainException;
import com.dreamhomes.haven.user.Role;
import org.springframework.http.HttpStatus;

/**
 * The submitter's role doesn't match the verification track they're trying to submit.
 * E.g. an APPLICANT can't submit AGENT_CREDENTIALS; an APPLICANT can't submit
 * OWNER_IDENTITY (own track is APPLICANT_IDENTITY).
 */
public class VerificationRoleMismatchException extends DomainException {

    public VerificationRoleMismatchException(VerificationType type, Role role) {
        super(HttpStatus.FORBIDDEN,
                "Role " + role + " cannot submit " + type + " verification");
    }
}
