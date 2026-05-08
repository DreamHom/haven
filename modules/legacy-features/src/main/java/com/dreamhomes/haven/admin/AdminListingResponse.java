package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingStatus;

import java.time.Instant;

/**
 * Admin response shape for listing actions. Adds {@code approvedAt} (the verified-listing
 * badge stamp) which the public listing response doesn't expose at the moment.
 */
public record AdminListingResponse(
        Long id,
        Long propertyId,
        Long ownerId,
        ListingStatus status,
        Instant approvedAt,
        Instant updatedAt
) {
    public static AdminListingResponse from(Listing l) {
        return new AdminListingResponse(
                l.getId(), l.getPropertyId(), l.getOwnerId(),
                l.getStatus(), l.getApprovedAt(), l.getUpdatedAt());
    }
}
