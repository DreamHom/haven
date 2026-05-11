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
    @Mapping(target = "status", source = "listing.status")
    @Mapping(target = "approvedAt", source = "listing.approvedAt")
    @Mapping(target = "viewCount", source = "listing.viewCount")
    @Mapping(target = "createdAt", source = "listing.createdAt")
    @Mapping(target = "updatedAt", source = "listing.updatedAt")
    @Mapping(target = "property", source = "property")
    ListingResponse toResponse(Listing listing, PropertySummary property);
}
