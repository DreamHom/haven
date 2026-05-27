package com.dreamhomes.haven.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Item 2 — request body for {@code POST /api/listings/{id}/photos/confirm}. The
 * client supplies the {@code fileKey} returned from the upload-url call plus the
 * actual {@code sizeBytes} it just PUT. Server HEADs R2 to verify the object exists
 * and the reported size matches.
 *
 * <p>Optional {@code width}/{@code height} let the caller seed gallery metadata for
 * future use (e.g. responsive image rendering); they are not validated against R2.</p>
 */
public record PhotoConfirmRequest(
        @Schema(description = "Object key returned by /upload-url. Foreign / unknown keys are 409.",
                example = "listings/17/abc-uuid-hero.jpg")
        @NotBlank
        String fileKey,

        @Schema(description = "MIME type that was uploaded.", example = "image/jpeg")
        @NotBlank
        String contentType,

        @Schema(description = "Actual bytes uploaded. 422 if mismatched against R2 HEAD.",
                example = "523456")
        @Positive
        long sizeBytes,

        @Schema(description = "Optional image width in pixels (metadata only).", example = "1920")
        @PositiveOrZero
        Integer width,

        @Schema(description = "Optional image height in pixels (metadata only).", example = "1280")
        @PositiveOrZero
        Integer height,

        @Schema(description = "Optional human-readable caption rendered next to the photo.",
                example = "Living room facing the lagoon")
        String caption
) {
}
