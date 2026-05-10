package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.listing.ListingStatus;

import java.time.Instant;

/**
 * Admin response shape for listing actions. Adds {@code approvedAt} (the verified-listing
 * badge stamp) which the public listing response doesn't expose. Construction lives in
 * {@code feature-admin-impl}.
 */
public record AdminListingResponse(
        Long id,
        Long propertyId,
        Long ownerId,
        ListingStatus status,
        Instant approvedAt,
        Instant updatedAt
) {
}
