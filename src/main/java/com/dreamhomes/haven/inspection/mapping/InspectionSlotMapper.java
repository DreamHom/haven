package com.dreamhomes.haven.inspection.mapping;

import com.dreamhomes.haven.inspection.dto.SlotResponse;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InspectionSlotMapper {
    SlotResponse toResponse(InspectionSlot slot);
}
