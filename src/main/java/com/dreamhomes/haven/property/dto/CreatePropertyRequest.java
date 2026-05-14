package com.dreamhomes.haven.property.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
        @Size(max = 5000) String description,
        @DecimalMin(value = "-90.0", inclusive = true)
        @DecimalMax(value = "90.0", inclusive = true)
        Double latitude,
        @DecimalMin(value = "-180.0", inclusive = true)
        @DecimalMax(value = "180.0", inclusive = true)
        Double longitude
) {
    public CreatePropertyCommand toCommand() {
        return new CreatePropertyCommand(type, address, bedrooms, bathrooms, sizeSqm, description,
                latitude, longitude);
    }

    @AssertTrue(message = "latitude and longitude must both be set or both omitted")
    public boolean latitudeAndLongitudePaired() {
        return (latitude == null && longitude == null)
                || (latitude != null && longitude != null);
    }
}
