package com.dreamhomes.haven.property.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.dreamhomes.haven.property.model.PropertyType;
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
