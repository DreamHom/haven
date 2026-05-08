package com.dreamhomes.haven.offer;

import jakarta.validation.constraints.NotNull;

public record RespondToOfferRequest(@NotNull OfferStatus status) {
}
