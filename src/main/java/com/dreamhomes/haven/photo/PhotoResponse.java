package com.dreamhomes.haven.photo;

import java.time.Instant;

public record PhotoResponse(
        Long id,
        Long listingId,
        String url,
        Integer displayOrder,
        String caption,
        Instant uploadedAt
) {
    public static PhotoResponse from(ListingPhoto p) {
        return new PhotoResponse(p.getId(), p.getListingId(), p.getUrl(),
                p.getDisplayOrder(), p.getCaption(), p.getUploadedAt());
    }
}
