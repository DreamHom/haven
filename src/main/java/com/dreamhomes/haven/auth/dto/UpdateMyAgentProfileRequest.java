package com.dreamhomes.haven.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Partial-update payload for {@code PATCH /api/me/agent-profile}. Every field is optional —
 * {@code null} means "leave the row's existing value alone", a present value means "replace".
 *
 * <p>For the four discovery-array fields, {@code null} = no change, {@code []} = clear all
 * entries. This lets the FE send {@code []} when the agent removes every tag without forcing
 * a special "clear" verb in the API.
 *
 * <p>{@code feeSchedule} mirrors the {@code agency} semantic: blank / empty after trim is
 * normalised to {@code null} in the column.
 */
public record UpdateMyAgentProfileRequest(
        @Size(max = 64)
        String licenseNumber,

        @Size(max = 255)
        String agency,

        @Size(max = 20, message = "serviceAreas may not exceed 20 entries")
        List<@NotBlank @Size(max = 64) String> serviceAreas,

        @Size(max = 20, message = "languages may not exceed 20 entries")
        List<@NotBlank @Size(max = 64) String> languages,

        @Size(max = 20, message = "specializationTags may not exceed 20 entries")
        List<@NotBlank @Size(max = 64) String> specializationTags,

        @Size(max = 1000)
        String feeSchedule
) {

    @AssertTrue(message = "at least one field must be provided")
    public boolean hasAnyField() {
        return licenseNumber != null
                || agency != null
                || serviceAreas != null
                || languages != null
                || specializationTags != null
                || feeSchedule != null;
    }

    @AssertTrue(message = "licenseNumber must not be blank when provided")
    public boolean isLicenseNumberPresentOrNonBlank() {
        return licenseNumber == null || !licenseNumber.isBlank();
    }
}
