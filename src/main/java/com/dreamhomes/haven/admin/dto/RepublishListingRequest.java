package com.dreamhomes.haven.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional reason for re-publishing a taken-down listing. Captured in the
 * audit log so the queue isn't silently reversed. Persona audit (Dayo):
 * "I re-published because the owner produced a deed of assignment matching
 * the photos — that justification has to live somewhere queryable."
 */
public record RepublishListingRequest(
        @Size(max = 1000) String reason
) {
}
