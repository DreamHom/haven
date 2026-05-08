package com.dreamhomes.haven.inspection;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateSlotRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
    public CreateSlotCommand toCommand() {
        return new CreateSlotCommand(startsAt, endsAt);
    }
}
