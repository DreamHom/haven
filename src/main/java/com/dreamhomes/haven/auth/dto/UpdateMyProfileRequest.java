package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.common.validation.StrictEmail;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
        @StrictEmail 
        String email,
        
        @Size(max = 255) 
        String fullName,
        
        @Size(max = 64) 
        String displayName,
        
        @Size(max = 32) 
        String phone
) {

    @AssertTrue(message = "at least one field must be provided")
    public boolean hasAnyField() {
        return email != null || fullName != null || displayName != null || phone != null;
    }

    @AssertTrue(message = "email must not be blank when provided")
    public boolean isEmailPresentOrNonBlank() {
        return email == null || !email.isBlank();
    }

    @AssertTrue(message = "fullName must not be blank when provided")
    public boolean isFullNamePresentOrNonBlank() {
        return fullName == null || !fullName.isBlank();
    }

    @AssertTrue(message = "displayName must not be blank when provided")
    public boolean isDisplayNamePresentOrNonBlank() {
        return displayName == null || !displayName.isBlank();
    }
}
