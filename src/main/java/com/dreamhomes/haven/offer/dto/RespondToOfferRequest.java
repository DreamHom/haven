package com.dreamhomes.haven.offer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.dreamhomes.haven.offer.model.OfferStatus;

/**
 * Body for {@code PATCH /api/offers/{id}}. The status enum is narrowed to the only
 * transitions this endpoint accepts — {@link Action#ACCEPTED} or
 * {@link Action#DECLINED}. {@code PENDING} and {@code COUNTERED} are not valid
 * here; counters go through {@code POST /api/offers/{id}/counter}.
 *
 * <p>Persona audit (Temi, Biodun) called out that the previous shape exposed all four
 * {@code OfferStatus} values via OpenAPI, leading frontends to build buttons for
 * invalid transitions.</p>
 *
 * <p>{@code reason} is an optional applicant-facing message — useful on decline to
 * tell the other party why ("offer too low", "want a different move-in date").</p>
 */
public record RespondToOfferRequest(
        @NotNull Action status,
        @Size(max = 1000) String reason) {

    public enum Action {
        ACCEPTED,
        DECLINED;

        public OfferStatus toOfferStatus() {
            return this == ACCEPTED ? OfferStatus.ACCEPTED : OfferStatus.DECLINED;
        }
    }
}
