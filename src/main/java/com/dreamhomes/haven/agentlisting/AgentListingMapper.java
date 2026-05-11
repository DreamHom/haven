package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.agentlisting.dto.AgentListingResponse;
import com.dreamhomes.haven.agentlisting.model.AgentListing;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AgentListingMapper {
    AgentListingResponse toResponse(AgentListing assignment);
}
