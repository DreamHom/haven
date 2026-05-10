package com.dreamhomes.haven.property.dto;

import java.math.BigDecimal;
import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.model.PropertyType;
/**
 * Inputs to {@link PropertyService#create} — already validated at the controller layer
 * by the request DTO's Bean Validation rules. Service code can trust the basics
 * (non-null fields, length caps, etc.) but still owns type-specific business rules.
 */
public record CreatePropertyCommand(
        PropertyType type,
        String address,
        Integer bedrooms,
        Integer bathrooms,
        BigDecimal sizeSqm,
        String description
) {
}
