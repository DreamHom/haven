package com.dreamhomes.haven.verification.automation;

/** Provider input for verifying property documents (C of O authenticity, lands registry lookup). */
public record PropertyDocumentCheckRequest(
        Long verificationId,
        Long submitterUserId,
        Long propertyId,
        String documentRefs
) {
}
