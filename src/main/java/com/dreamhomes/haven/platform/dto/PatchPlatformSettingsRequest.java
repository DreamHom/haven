package com.dreamhomes.haven.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Schema(description = "Shallow-merge patch for top-level keys in `platform_settings.settings`.")
public record PatchPlatformSettingsRequest(
        @NotNull
        @NotEmpty
        @Schema(description = "Keys to merge into the existing JSON object (nested objects replace wholesale).")
        Map<String, Object> patch
) {
}
