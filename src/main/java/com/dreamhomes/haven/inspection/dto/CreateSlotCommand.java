package com.dreamhomes.haven.inspection.dto;

import java.time.Instant;

public record CreateSlotCommand(Instant startsAt, Instant endsAt) {
}
