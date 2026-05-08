package com.dreamhomes.haven.property;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Public contract for the property feature. Cross-feature consumers wire this interface —
 * they never see the {@code Property} entity, the repository, or any other implementation
 * detail. The implementation is {@code com.dreamhomes.haven.property.PropertyService} in
 * {@code feature-property-impl}.
 */
public interface PropertyApi {

    /**
     * Read for cross-feature consumers. Returns the rich response shape; throws
     * {@link PropertyNotFoundException} on miss.
     */
    PropertyResponse findById(Long propertyId);

    /** Read for embedded contexts (browse cards). Empty optional on miss. */
    Optional<PropertySummary> findSummary(Long propertyId);

    /** Batch summary lookup keyed by id. Useful for browse listings without N+1. */
    Map<Long, PropertySummary> findSummariesByIds(Collection<Long> propertyIds);

    /** Returns the owner user id of the property; empty optional if the property is gone. */
    Optional<Long> ownerOf(Long propertyId);

    /**
     * Stamp {@code documentsVerifiedAt} on a property. Called by the admin verification
     * workflow when a {@code PROPERTY_DOCUMENTS} verification is approved. Throws
     * {@link PropertyNotFoundException} if the property has been deleted between
     * submission and decision.
     */
    void markDocumentsVerified(Long propertyId, Instant when);
}
