package com.dreamhomes.haven.property.dto;

import java.math.BigDecimal;

/**
 * Partial property update — only non-null fields from the request are applied.
 * {@code latitude} / {@code longitude} must appear together or not at all.
 */
public record UpdatePropertyCommand(
        String address,
        Integer bedrooms,
        Integer bathrooms,
        BigDecimal sizeSqm,
        String description,
        Double latitude,
        Double longitude) {
}
