package com.dreamhomes.haven.property;

import java.math.BigDecimal;

/**
 * Lightweight projection of {@link Property} for embedding in other resources
 * (most importantly, listing browse responses) so a frontend can render a card
 * without a follow-up GET.
 */
public record PropertySummary(
        Long id,
        PropertyType type,
        String address,
        Integer bedrooms,
        Integer bathrooms,
        BigDecimal sizeSqm
) {
    public static PropertySummary from(Property p) {
        return new PropertySummary(
                p.getId(), p.getType(), p.getAddress(),
                p.getBedrooms(), p.getBathrooms(), p.getSizeSqm());
    }
}
