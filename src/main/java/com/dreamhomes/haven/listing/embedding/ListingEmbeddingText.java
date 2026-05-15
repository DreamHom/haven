package com.dreamhomes.haven.listing.embedding;

import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.property.dto.PropertySummary;

/**
 * Single string fed to the embedding model — keep in sync with fields exposed to Claude in Dream AI.
 */
public final class ListingEmbeddingText {

    private ListingEmbeddingText() {
    }

    public static String format(Listing listing, PropertySummary property) {
        StringBuilder sb = new StringBuilder();
        sb.append(listing.getListingType()).append(' ');
        sb.append(listing.getAskingPrice()).append(' ').append(listing.getCurrency()).append('\n');
        appendLine(sb, "title", listing.getTitle());
        appendLine(sb, "headline", listing.getHeadline());
        appendLine(sb, "description", listing.getDescription());
        appendLine(sb, "virtualTour", listing.getVirtualTourUrl());
        appendLine(sb, "floorPlan", listing.getFloorPlanUrl());
        if (listing.getPetsAllowed() != null) {
            appendLine(sb, "pets", listing.getPetsAllowed());
        }
        if (listing.getUtilitiesNote() != null) {
            appendLine(sb, "utilities", listing.getUtilitiesNote());
        }
        sb.append("priceNegotiable:").append(listing.isPriceNegotiable()).append('\n');
        if (property != null) {
            appendLine(sb, "address", property.address());
            if (property.bedrooms() != null) {
                sb.append("bedrooms:").append(property.bedrooms()).append('\n');
            }
            if (property.bathrooms() != null) {
                sb.append("bathrooms:").append(property.bathrooms()).append('\n');
            }
            if (property.type() != null) {
                sb.append("propertyType:").append(property.type()).append('\n');
            }
            if (property.latitude() != null && property.longitude() != null) {
                sb.append("lat:").append(property.latitude()).append(" lng:").append(property.longitude()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append(label).append(": ").append(value.trim()).append('\n');
    }
}
