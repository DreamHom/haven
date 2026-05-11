package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.admin.dto.AnalyticsSummaryResponse;
import com.dreamhomes.haven.admin.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @Operation(
            summary = "Platform-health analytics summary",
            description = """
                    One-shot snapshot for the admin dashboard. Returns six aggregated counts:

                    - `totalUsers` — every registered user, regardless of role or suspension.
                    - `suspendedUsers` — users with `suspended_at` stamped (admin-actioned, \
                      not self-deleted).
                    - `openListings` — listings currently visible to public discovery (`LIVE`).
                    - `closedListings` — listings whose deal has completed (`CLOSED`).
                    - `pendingVerifications` — Dayo's work-queue depth (`PENDING` rows in \
                      the `verifications` table).
                    - `pendingOffers` — open deals where someone owes a response (`PENDING` \
                      rows in the `offers` table).

                    Each count is a single index-backed query; the endpoint runs in O(1) \
                    regardless of table size.

                    **Role gate**: `ADMIN`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Aggregate counts for the dashboard.",
                    content = @Content(
                            schema = @Schema(implementation = AnalyticsSummaryResponse.class),
                            examples = @ExampleObject(name = "TypicalSummary", value = """
                                    { "totalUsers": 1284, "suspendedUsers": 3,
                                      "openListings": 412, "closedListings": 187,
                                      "pendingVerifications": 8, "pendingOffers": 47 }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary() {
        return adminAnalyticsService.summary();
    }
}
