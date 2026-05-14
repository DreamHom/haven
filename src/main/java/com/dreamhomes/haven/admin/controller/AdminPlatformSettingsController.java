package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.platform.PlatformSettingsService;
import com.dreamhomes.haven.platform.dto.PatchPlatformSettingsRequest;
import com.dreamhomes.haven.platform.dto.PlatformSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/platform-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminPlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    @Operation(summary = "Read platform settings", description = "Singleton JSON document at `platform_settings.id = 1`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current settings."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public PlatformSettingsResponse get() {
        return platformSettingsService.get();
    }

    @Operation(summary = "Merge platform settings",
            description = "Shallow-merges `patch` keys into the existing JSON object.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated settings."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping
    public PlatformSettingsResponse patch(@Valid @RequestBody PatchPlatformSettingsRequest request) {
        return platformSettingsService.merge(request);
    }
}
