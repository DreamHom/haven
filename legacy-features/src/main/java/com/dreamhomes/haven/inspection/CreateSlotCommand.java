package com.dreamhomes.haven.inspection;

import java.time.Instant;

public record CreateSlotCommand(Instant startsAt, Instant endsAt) {
}
