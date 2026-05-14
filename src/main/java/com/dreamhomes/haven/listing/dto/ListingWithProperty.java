package com.dreamhomes.haven.listing.dto;

import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.listing.model.Listing;

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
 */
public record ListingWithProperty(Listing listing, PropertySummary property, String ownerPublicBio) {
}
