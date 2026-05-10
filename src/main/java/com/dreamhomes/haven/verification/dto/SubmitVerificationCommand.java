package com.dreamhomes.haven.verification.dto;

import java.util.Map;
import com.dreamhomes.haven.verification.model.VerificationType;

/**
 * Service-layer command for submitting a verification. {@code propertyId} is required
 * for {@link VerificationType#PROPERTY_DOCUMENTS} and ignored for the others.
 *
 * <p>{@code documentRefs} is the free-shape JSON metadata about the submitted
 * documents — kind/ref pairs and similar (PRD §6: metadata only, no raw files).
 * The service serialises this into the {@code document_refs} JSONB column.
 */
public record SubmitVerificationCommand(
        VerificationType type,
        Long propertyId,
        Map<String, Object> documentRefs) {
}
