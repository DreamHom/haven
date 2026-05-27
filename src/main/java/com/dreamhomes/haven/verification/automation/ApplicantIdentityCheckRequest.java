package com.dreamhomes.haven.verification.automation;

/** Provider input for verifying an applicant's identity (NIN match). */
public record ApplicantIdentityCheckRequest(
        Long verificationId,
        Long submitterUserId,
        String documentRefs
) {
}
