package com.dreamhomes.haven.property;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight projection of {@link Property} for embedding in other resources
 * (most importantly, listing browse responses) so a frontend can render a card
 * without a follow-up GET.
 *
 * <p>{@code documentsVerifiedAt} is the public-trust signal — non-null means an admin
 * has approved this property's docs (PRD §4.1, §4.8). Carrying it on the summary lets
 * browse cards show the verified-documents badge without a separate request.
 */
public record PropertySummary(
        Long id,
        PropertyType type,
        String address,
        Integer bedrooms,
        Integer bathrooms,
        BigDecimal sizeSqm,
        Instant documentsVerifiedAt
) {
    public static PropertySummary from(Property p) {
        return new PropertySummary(
                p.getId(), p.getType(), p.getAddress(),
                p.getBedrooms(), p.getBathrooms(), p.getSizeSqm(),
                p.getDocumentsVerifiedAt());
    }
}
