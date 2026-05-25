package com.dreamhomes.haven.listing.exception;

import com.dreamhomes.haven.common.DomainException;
import com.dreamhomes.haven.listing.model.ListingType;
import org.springframework.http.HttpStatus;

/**
 * Owner (or a racing duplicate request) tried to publish a second LIVE listing of the
 * same {@link ListingType} on a property that already has one. The design intent is
 * "at most one LIVE RENT and at most one LIVE SALE per property" — see Item 12 in
 * {@code docs/demo-prep/post-session-tasks.md} and the partial unique index from V47.
 *
 * <p>Throws 409 with a specialised {@code type} URI suffix so frontends can branch on
 * "duplicate active listing" specifically and render a one-click "close the existing
 * listing first" hint, instead of the generic conflict copy.
 */
public class ListingDuplicateOpenForTypeException extends DomainException {

    public static final String TYPE_SUFFIX =
            "listing.duplicate-open-listing-for-property-and-type";

    public ListingDuplicateOpenForTypeException(Long propertyId, ListingType listingType) {
        super(HttpStatus.CONFLICT,
                "Property " + propertyId + " already has an active " + listingType
                        + " listing — close it before publishing a new one",
                TYPE_SUFFIX);
    }
}
