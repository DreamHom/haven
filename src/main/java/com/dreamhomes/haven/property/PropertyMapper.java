package com.dreamhomes.haven.property;

import com.dreamhomes.haven.property.dto.PropertyResponse;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.property.model.Property;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PropertyMapper {
    PropertySummary toSummary(Property property);

    PropertyResponse toResponse(Property property);
}
