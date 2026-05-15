package com.dreamhomes.haven.dreamai.turn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * AI-generated reasoning for a compare turn. Carries the model's overall recommendation
 * + per-listing pros/cons/bestFor notes. Surfaced as a {@link TurnBlock#compareReasoning}
 * payload alongside the existing {@code compareListingIds} layout signal so the frontend
 * can show both the side-by-side cards AND the AI's case for each.
 *
 * <p>The {@code recommendedListingId} is the model's pick if any one listing clearly
 * fits the user's intent better than the others; null when the model declined to choose
 * (e.g. "all three meet your stated needs — pick on personal preference").</p>
 */
@Schema(description = "AI's structured recommendation across the compared listings.")
public record CompareReasoning(
        @Schema(description = "The id the model thinks fits the user's intent best — null when no clear winner.",
                example = "12", nullable = true)
        Long recommendedListingId,

        @Schema(description = "Markdown summary of why that listing was picked, or why the field is too even to call.",
                example = "All three are within budget; #12 wins on the school-run constraint Adaeze mentioned.")
        String summary,

        @Schema(description = "Per-listing reasoning, one entry per id sent to the model (in input order).")
        List<PerListingNote> perListing
) {
    @JsonCreator
    public CompareReasoning(
            @JsonProperty("recommendedListingId") Long recommendedListingId,
            @JsonProperty("summary") String summary,
            @JsonProperty("perListing") List<PerListingNote> perListing) {
        this.recommendedListingId = recommendedListingId;
        this.summary = summary;
        this.perListing = perListing == null ? List.of() : List.copyOf(perListing);
    }
}
