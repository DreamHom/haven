package com.dreamhomes.haven.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /api/admin/listings/{id}/takedown}. Reason is required. */
public record TakedownListingRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
