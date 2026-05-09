package com.dreamhomes.haven.domain.verification.dto;

import com.dreamhomes.haven.domain.verification.model.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitVerificationRequest(
        @NotNull 
        Long subjectUserId,
        
        Long propertyId,
        
        @NotNull 
        VerificationType type,
        
        @NotBlank 
        String documentUrl
) {}