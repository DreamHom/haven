package com.dreamhomes.haven.property.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.dreamhomes.haven.property.model.PropertyType;
/**
 * Lightweight projection of a property for embedding in other resources (most importantly,
 * listing browse responses) so a frontend can render a card without a follow-up GET.
 *
 * <p>{@code documentsVerifiedAt} is the public-trust signal — non-null means an admin has
 * approved this property's docs (PRD §4.1, §4.8). Carrying it on the summary lets browse
 * cards show the verified-documents badge without a separate request.
 *
 * <p>Construction lives in {@code feature-property-impl}; this record is a pure data
 * shape with no entity coupling.
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
}
