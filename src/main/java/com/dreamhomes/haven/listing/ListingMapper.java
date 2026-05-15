package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.property.dto.PropertySummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
    @Mapping(target = "status", source = "listing.status")
    @Mapping(target = "approvedAt", source = "listing.approvedAt")
    @Mapping(target = "viewCount", source = "listing.viewCount")
    @Mapping(target = "createdAt", source = "listing.createdAt")
    @Mapping(target = "updatedAt", source = "listing.updatedAt")
    @Mapping(target = "property", source = "property")
    @Mapping(target = "assignedAgentId", source = "assignedAgentId")
    @Mapping(target = "pendingReportCount", source = "pendingReportCount")
    ListingResponse toResponse(Listing listing, PropertySummary property,
                               Long assignedAgentId, Long pendingReportCount);

    /**
     * List/browse callsites don't pay the cost of looking up trust signals (each one is
     * an extra query per row). Detail endpoints use the 4-arg overload after resolving
     * {@code assignedAgentId} + {@code pendingReportCount}.
     */
    default ListingResponse toResponse(Listing listing, PropertySummary property) {
        return toResponse(listing, property, null, null);
    }
}
