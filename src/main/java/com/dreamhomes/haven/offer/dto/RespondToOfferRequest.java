package com.dreamhomes.haven.offer.dto;

import jakarta.validation.constraints.NotNull;
import com.dreamhomes.haven.offer.model.OfferStatus;
public record RespondToOfferRequest(@NotNull OfferStatus status) {
}
