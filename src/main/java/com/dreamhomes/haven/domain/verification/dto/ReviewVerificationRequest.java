package com.dreamhomes.haven.domain.verification.dto;

import com.dreamhomes.haven.domain.verification.model.VerificationStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewVerificationRequest(
        @NotNull VerificationStatus status
) {}

