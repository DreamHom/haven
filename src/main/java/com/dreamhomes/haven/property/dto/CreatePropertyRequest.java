package com.dreamhomes.haven.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import com.dreamhomes.haven.property.model.PropertyType;
public record CreatePropertyRequest(
        @NotNull PropertyType type,
        @NotBlank @Size(max = 500) String address,
        @PositiveOrZero Integer bedrooms,
        @PositiveOrZero Integer bathrooms,
        @Positive BigDecimal sizeSqm,
        @Size(max = 5000) String description
) {
    public CreatePropertyCommand toCommand() {
        return new CreatePropertyCommand(type, address, bedrooms, bathrooms, sizeSqm, description);
    }
}
