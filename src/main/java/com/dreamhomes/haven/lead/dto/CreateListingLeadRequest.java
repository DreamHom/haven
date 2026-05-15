package com.dreamhomes.haven.lead.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Applicant interest on a listing — contact fields gated until owner reveal.")
public record CreateListingLeadRequest(
        @Size(max = 2000)
        String message,

        @NotBlank
        @Size(max = 64)
        String contactPhone,

        @NotBlank
        @Email
        @Size(max = 255)
        String contactEmail
) {
}
