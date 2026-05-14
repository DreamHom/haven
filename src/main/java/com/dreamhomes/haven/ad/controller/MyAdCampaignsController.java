package com.dreamhomes.haven.ad.controller;

import com.dreamhomes.haven.ad.AdCampaignService;
import com.dreamhomes.haven.ad.dto.AdCampaignResponse;
import com.dreamhomes.haven.ad.dto.CreateAdCampaignRequest;
import com.dreamhomes.haven.ad.dto.PatchMyAdCampaignRequest;
import com.dreamhomes.haven.auth.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/ad-campaigns")
@RequiredArgsConstructor
@Tag(name = "Ads")
public class MyAdCampaignsController {

    private final AdCampaignService adCampaignService;

    @Operation(summary = "Create my ad campaign", description = "Creates a `DRAFT` row owned by the JWT subject.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft created."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdCampaignResponse create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateAdCampaignRequest request) {
        return adCampaignService.create(principal.userId(), request);
    }

    @Operation(summary = "List my ad campaigns", description = "Newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated campaigns."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public Page<AdCampaignResponse> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return adCampaignService.listMine(principal.userId(), pageable);
    }

    @Operation(summary = "Update my ad campaign",
            description = "Edits `title` / `body` / `budgetCents` only in `DRAFT`. Sponsor may set `status` to `PENDING_REVIEW` once to submit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    public AdCampaignResponse patch(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Campaign id.", example = "3")
            @PathVariable Long id,
            @Valid @RequestBody PatchMyAdCampaignRequest request) {
        return adCampaignService.patchMine(principal.userId(), id, request);
    }
}
