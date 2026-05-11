package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.offer.dto.OfferResponse;
import com.dreamhomes.haven.offer.model.Offer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OfferMapper {
    OfferResponse toResponse(Offer offer);
}
