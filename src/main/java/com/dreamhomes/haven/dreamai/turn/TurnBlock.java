package com.dreamhomes.haven.dreamai.turn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Discriminated union for assistant blocks — see {@code type}.
 */
@Schema(description = "Polymorphic assistant block. Exactly one payload shape should be populated per `type`.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TurnBlock(
        @Schema(description = "listings | compare | chips", requiredMode = Schema.RequiredMode.REQUIRED, example = "listings")
        String type,
        @Schema(description = "When type=listings — LIVE ids the server validated.", nullable = true)
        @JsonProperty("listingIds")
        List<Long> listingIds,
        @Schema(description = "When type=compare — ids included in the comparison projection.", nullable = true)
        @JsonProperty("compareListingIds")
        List<Long> compareListingIds,
        @Schema(description = "When type=chips — quick-reply options for clarify flows.", nullable = true)
        List<ChipOption> options,
        @Schema(description = "When type=compare — structured AI reasoning attached to the compared ids. "
                + "Null on the legacy stub-compare path; populated when the orchestrator ran the Claude-backed compare.",
                nullable = true)
        @JsonProperty("compareReasoning")
        CompareReasoning compareReasoning
) {
    @JsonCreator
    public TurnBlock(
            @JsonProperty("type") String type,
            @JsonProperty("listingIds") List<Long> listingIds,
            @JsonProperty("compareListingIds") List<Long> compareListingIds,
            @JsonProperty("options") List<ChipOption> options,
            @JsonProperty("compareReasoning") CompareReasoning compareReasoning) {
        this.type = type;
        this.listingIds = listingIds == null ? List.of() : List.copyOf(listingIds);
        this.compareListingIds = compareListingIds == null ? List.of() : List.copyOf(compareListingIds);
        this.options = options == null ? List.of() : List.copyOf(options);
        this.compareReasoning = compareReasoning;
    }

    public static TurnBlock listings(List<Long> ids) {
        return new TurnBlock("listings", ids, List.of(), List.of(), null);
    }

    public static TurnBlock compare(List<Long> ids) {
        return new TurnBlock("compare", List.of(), ids, List.of(), null);
    }

    /**
     * Compare block enriched with AI reasoning — the same {@code compareListingIds}
     * the legacy {@link #compare(List)} carries, plus a {@link CompareReasoning} payload
     * with the model's recommended id + per-listing pros/cons/best-for notes.
     */
    public static TurnBlock compareWithReasoning(List<Long> ids, CompareReasoning reasoning) {
        return new TurnBlock("compare", List.of(), ids, List.of(), reasoning);
    }

    public static TurnBlock chips(List<ChipOption> options) {
        return new TurnBlock("chips", List.of(), List.of(), options, null);
    }
}
