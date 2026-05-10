package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.common.validation.NotCommonPassword;
import com.dreamhomes.haven.common.validation.StrictEmail;
import com.dreamhomes.haven.user.model.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @StrictEmail String email,
        @NotBlank @Size(min = 8, max = 100) @NotCommonPassword String password,
        @NotBlank @Size(max = 255) String fullName,
        /**
         * Optional short handle the UI renders on tight surfaces. If null/blank the
         * server defaults it to the first whitespace-delimited token of
         * {@link #fullName} (so "Amaka Chinwe Okafor" → "Amaka") — sensible default
         * for the most common Nigerian-name shapes; users can override via profile
         * edit later.
         */
        @Size(max = 64) String displayName,
        @Size(max = 32) String phone,
        @NotNull Role role,
        @Size(max = 64) String licenseNumber
) {
    /** PRD: admins are seeded only — never accept ADMIN role through self-registration. */
    @AssertTrue(message = "role must not be ADMIN")
    public boolean isPublicRole() {
        return role != Role.ADMIN;
    }

    /** PRD: every agent must register a real-estate licence number. */
    @AssertTrue(message = "licenseNumber is required when role is AGENT")
    public boolean isAgentLicenseProvidedWhenNeeded() {
        return role != Role.AGENT || (licenseNumber != null && !licenseNumber.isBlank());
    }

}
