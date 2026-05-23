package com.dreamhomes.haven.property.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdatePropertyRequest(
        @Size(max = 500) String address,
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

    public UpdatePropertyCommand toCommand() {
        return new UpdatePropertyCommand(
                trimToNull(address),
                bedrooms,
                bathrooms,
                sizeSqm,
                trimToNull(description),
                latitude,
                longitude);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @AssertTrue(message = "at least one field must be provided")
    public boolean hasAnyField() {
        return address != null || bedrooms != null || bathrooms != null || sizeSqm != null
                || description != null || latitude != null || longitude != null;
    }

    @AssertTrue(message = "address must not be blank when provided")
    public boolean addressNonBlankWhenPresent() {
        return address == null || !address.isBlank();
    }

    @AssertTrue(message = "latitude and longitude must both be set or both omitted")
    public boolean latitudeAndLongitudePaired() {
        return (latitude == null && longitude == null)
                || (latitude != null && longitude != null);
    }
}
