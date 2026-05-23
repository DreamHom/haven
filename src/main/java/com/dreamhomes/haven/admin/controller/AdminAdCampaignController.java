package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.ad.AdCampaignService;
import com.dreamhomes.haven.ad.dto.AdminPatchAdCampaignRequest;
import com.dreamhomes.haven.ad.dto.AdCampaignResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ad-campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminAdCampaignController {

    private final AdCampaignService adCampaignService;

    @Operation(summary = "List ad campaigns", description = "Platform-wide, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated campaigns."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public Page<AdCampaignResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return adCampaignService.adminList(pageable);
    }

    @Operation(summary = "Set ad campaign status", description = "Admin moderation / lifecycle write.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    public AdCampaignResponse patch(
            @Parameter(description = "Campaign id.", example = "3")
            @PathVariable Long id,
            @Valid @RequestBody AdminPatchAdCampaignRequest request) {
        return adCampaignService.adminPatch(id, request);
    }
}
