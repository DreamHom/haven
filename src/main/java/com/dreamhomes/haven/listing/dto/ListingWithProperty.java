package com.dreamhomes.haven.listing.dto;

import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.listing.model.Listing;

import java.time.Instant;

/**
 * Service-internal pairing: a listing alongside its parent property's summary. Used so
 * the read paths can return both in one shot without forcing the controller to call
 * the property API itself.
 *
 * <p>Holds {@link PropertySummary} (not the entity) so this module never imports the
 * Property impl. Cross-aggregate reference stays through {@code feature-property-api}.
 *
 * <p>{@code ownerPublicBio} is the listing owner's {@code users.public_bio} when loaded
 * for public browse/detail (nullable when unset or not fetched).
 *
 * <p>{@code ownerIdentityVerifiedAt} is the moment the listing owner's identity
 * verification was approved by an admin (PRD §4.8). Null when the owner has not yet
 * completed identity verification — drives the "⚠️ Possible Scam" warning chip on
 * listing cards / detail (see Item 16 in {@code docs/demo-prep/post-session-tasks.md}).
 */
public record ListingWithProperty(
        Listing listing,
        PropertySummary property,
        String ownerPublicBio,
        Instant ownerIdentityVerifiedAt) {

    /**
     * Back-compat factory for call sites that haven't loaded owner verification yet.
     * New code should prefer the 4-arg canonical constructor and surface the trust
     * signal explicitly.
     */
    public ListingWithProperty(Listing listing, PropertySummary property, String ownerPublicBio) {
        this(listing, property, ownerPublicBio, null);
    }
}
