package com.dreamhomes.haven.domain.property.dto;

import com.dreamhomes.haven.domain.property.model.PropertyStatus;

public record PropertyResponse(
        Long id,
        Long ownerId,
        String addressLine1,
        String city,
        PropertyStatus status
) {}

