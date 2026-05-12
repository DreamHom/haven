package com.dreamhomes.haven.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record UpdateMyAgentProfileRequest(
        @Size(max = 64) 
        String licenseNumber,
        
        @Size(max = 255) 
        String agency
) {

    @AssertTrue(message = "at least one field must be provided")
    public boolean hasAnyField() {
        return licenseNumber != null || agency != null;
    }

    @AssertTrue(message = "licenseNumber must not be blank when provided")
    public boolean isLicenseNumberPresentOrNonBlank() {
        return licenseNumber == null || !licenseNumber.isBlank();
    }
}
