package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.Property;

/**
 * Service-internal pairing: a listing alongside its parent property. Used so the
 * read paths can return both in one shot without forcing the controller to call
 * the property repo itself.
 */
public record ListingWithProperty(Listing listing, Property property) {
}
