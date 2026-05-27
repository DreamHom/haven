package com.dreamhomes.haven.property.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * <p>{@code latitude} / {@code longitude} are WGS-84 when the owner supplied coordinates
 * at property registration; both null for legacy rows or when not captured.
 */
public record PropertySummary(
        Long id,
        PropertyType type,
        String address,
        Integer bedrooms,
        Integer bathrooms,
        BigDecimal sizeSqm,
        @Schema(
                description = """
                        Property-document verification timestamp. **Non-null = an admin has \
                        approved the property's title/registry documents.** UI should render \
                        a "✓ Verified" green badge on listings where this is non-null. Null \
                        means baseline (no badge); ownership of the property itself is not \
                        vouched for. Pair with {@code ListingResponse.ownerIdentityVerifiedAt} \
                        for the full trust-signal matrix (Item 16, post-session-tasks.md).
                        """,
                example = "2026-04-12T10:00:00Z",
                nullable = true)
        Instant documentsVerifiedAt,
        Double latitude,
        Double longitude
) {
}
