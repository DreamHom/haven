package com.dreamhomes.haven.domain.inspection.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateSlotRequest(
        @NotNull 
        Long listingId,

        @NotNull 
        Long agentId,
        
        @NotNull 
        Instant startAt,
        
        @NotNull 
        @NotNull Instant endAt
) {}

