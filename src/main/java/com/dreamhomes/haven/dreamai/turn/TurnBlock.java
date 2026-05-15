package com.dreamhomes.haven.dreamai.turn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Discriminated union for assistant blocks — see {@code type}.
 */
@Schema(description = "Polymorphic assistant block. Exactly one payload shape should be populated per `type`.")
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
        List<ChipOption> options
) {
    @JsonCreator
    public TurnBlock(
            @JsonProperty("type") String type,
            @JsonProperty("listingIds") List<Long> listingIds,
            @JsonProperty("compareListingIds") List<Long> compareListingIds,
            @JsonProperty("options") List<ChipOption> options) {
        this.type = type;
        this.listingIds = listingIds == null ? List.of() : List.copyOf(listingIds);
        this.compareListingIds = compareListingIds == null ? List.of() : List.copyOf(compareListingIds);
        this.options = options == null ? List.of() : List.copyOf(options);
    }

    public static TurnBlock listings(List<Long> ids) {
        return new TurnBlock("listings", ids, List.of(), List.of());
    }

    public static TurnBlock compare(List<Long> ids) {
        return new TurnBlock("compare", List.of(), ids, List.of());
    }

    public static TurnBlock chips(List<ChipOption> options) {
        return new TurnBlock("chips", List.of(), List.of(), options);
    }
}
