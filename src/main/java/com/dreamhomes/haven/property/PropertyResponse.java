package com.dreamhomes.haven.property;

import java.math.BigDecimal;
import java.time.Instant;

public record PropertyResponse(
        Long id,
        Long ownerId,
        PropertyType type,
        String address,
        Integer bedrooms,
        Integer bathrooms,
        BigDecimal sizeSqm,
        String description,
        Instant createdAt
) {
}
