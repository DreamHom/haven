package com.dreamhomes.haven.verification.automation;

/**
 * Provider input for verifying an owner's identity. Wraps the verification id +
 * submitter user id so providers can correlate, plus the raw {@code documentRefs}
 * JSON the submitter uploaded.
 */
public record OwnerIdentityCheckRequest(
        Long verificationId,
        Long submitterUserId,
        String documentRefs
) {
}
