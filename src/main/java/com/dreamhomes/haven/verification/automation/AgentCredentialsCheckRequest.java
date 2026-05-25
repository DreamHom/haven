package com.dreamhomes.haven.verification.automation;

/** Provider input for verifying an agent's professional credentials (licence lookup). */
public record AgentCredentialsCheckRequest(
        Long verificationId,
        Long submitterUserId,
        String documentRefs
) {
}
