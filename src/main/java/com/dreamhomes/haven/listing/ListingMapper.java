package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.property.dto.PropertySummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface ListingMapper {
    /**
     * The {@code property} field on {@link ListingResponse} is the embedded summary.
     * Both arguments expose an {@code id}, so we pin every source explicitly to
     * avoid MapStruct's "ambiguous source property" error.
     */
    @Mapping(target = "id", source = "listing.id")
    @Mapping(target = "propertyId", source = "listing.propertyId")
    @Mapping(target = "ownerId", source = "listing.ownerId")
    @Mapping(target = "listingType", source = "listing.listingType")
    @Mapping(target = "askingPrice", source = "listing.askingPrice")
    @Mapping(target = "currency", source = "listing.currency")
    @Mapping(target = "cautionFee", source = "listing.cautionFee")
    @Mapping(target = "serviceCharge", source = "listing.serviceCharge")
    @Mapping(target = "agencyFee", source = "listing.agencyFee")
    @Mapping(target = "title", source = "listing.title")
    @Mapping(target = "description", source = "listing.description")
    @Mapping(target = "headline", source = "listing.headline")
    @Mapping(target = "handoverDate", source = "listing.handoverDate")
    @Mapping(target = "virtualTourUrl", source = "listing.virtualTourUrl")
    @Mapping(target = "floorPlanUrl", source = "listing.floorPlanUrl")
    @Mapping(target = "priceNegotiable", source = "listing.priceNegotiable")
    @Mapping(target = "status", source = "listing.status")
    @Mapping(target = "approvedAt", source = "listing.approvedAt")
    @Mapping(target = "viewCount", source = "listing.viewCount")
    @Mapping(target = "createdAt", source = "listing.createdAt")
    @Mapping(target = "updatedAt", source = "listing.updatedAt")
    @Mapping(target = "property", source = "property")
    @Mapping(target = "assignedAgentId", source = "assignedAgentId")
    @Mapping(target = "pendingReportCount", source = "pendingReportCount")
    @Mapping(target = "petsAllowed", source = "listing.petsAllowed")
    @Mapping(target = "utilitiesNote", source = "listing.utilitiesNote")
    @Mapping(target = "ownerPublicBio", source = "ownerPublicBio")
    @Mapping(target = "ownerIdentityVerifiedAt", source = "ownerIdentityVerifiedAt")
    ListingResponse toResponse(Listing listing, PropertySummary property,
                               Long assignedAgentId, Long pendingReportCount,
                               String ownerPublicBio, Instant ownerIdentityVerifiedAt);

    /**
     * Back-compat overload — callers that haven't loaded owner trust pass null.
     * Prefer the 6-arg form on public surfaces so the "⚠️ Possible Scam" warning
     * chip can be rendered without an N+1 fetch (Item 16, post-session-tasks.md).
     */
    default ListingResponse toResponse(Listing listing, PropertySummary property,
                                       Long assignedAgentId, Long pendingReportCount,
                                       String ownerPublicBio) {
        return toResponse(listing, property, assignedAgentId, pendingReportCount,
                ownerPublicBio, null);
    }

    /**
     * List/browse callsites don't pay the cost of looking up trust signals (each one is
     * an extra query per row). Detail endpoints use the 4-arg overload after resolving
     * {@code assignedAgentId} + {@code pendingReportCount} + {@code ownerPublicBio}.
     */
    default ListingResponse toResponse(Listing listing, PropertySummary property) {
        return toResponse(listing, property, null, null, null, null);
    }
}
