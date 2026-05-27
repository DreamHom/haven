package com.dreamhomes.haven.review.dto;

import com.dreamhomes.haven.listing.model.ListingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Read-only snapshot of the caller's ability to review the owner and/or the assigned
 * agent of a listing (Item 9, post-session-tasks.md). Returned with HTTP 200 even when
 * neither flag is true — eligibility is data, not an error. Vista uses this to
 * conditionally render "Review the owner" / "Review the agent" CTAs on the listing
 * detail page after a deal closes.
 *
 * <p>The {@code reasons} block carries a short, user-facing explanation for whichever
 * side is not eligible — empty string when eligibility is true.
 */
@Schema(description = "Whether the caller can review the listing's owner and/or assigned agent.")
public record ReviewEligibilityResponse(

        @Schema(description = "Current status of the listing — eligibility is only \"true\" when CLOSED.",
                example = "CLOSED")
        ListingStatus listingStatus,

        @Schema(description = "True when the caller can post a review on the listing's owner.",
                example = "true")
        boolean canReviewOwner,

        @Schema(description = "True when the caller can post a review on the listing's assigned agent.",
                example = "false")
        boolean canReviewAgent,

        @Schema(description = "Listing owner's user id (always present).", example = "42")
        Long ownerUserId,

        @Schema(description = "Listing's currently-ACCEPTED agent's user id, or null when no agent.",
                example = "23", nullable = true)
        Long agentUserId,

        @Schema(description = "Why each side is not eligible — null when eligible.")
        Reasons reasons
) {

    /**
     * Short, user-facing reason strings keyed by reviewable role. {@code null} means
     * the corresponding {@code canReview*} flag is true.
     */
    @Schema(description = "Per-role eligibility reasons; null entries indicate eligibility.")
    public record Reasons(
            @Schema(description = "Reason the caller cannot review the owner (null if eligible).",
                    example = "You cannot review yourself", nullable = true)
            String owner,

            @Schema(description = "Reason the caller cannot review the agent (null if eligible, or no agent assigned).",
                    example = "Not an accepted-offer party on this listing", nullable = true)
            String agent
    ) {
    }
}
