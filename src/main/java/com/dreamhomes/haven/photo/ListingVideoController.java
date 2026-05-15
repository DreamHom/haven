package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.photo.dto.PhotoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Listing videos")
public class ListingVideoController {

    private final ListingVideoService listingVideoService;

    public record AddListingVideoRequest(
            @NotBlank @Size(max = 512) String url,
            @Size(max = 255) String caption
    ) {
    }

    @Operation(summary = "Add a video URL to a listing (owner)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Video metadata stored.",
                    content = @Content(schema = @Schema(implementation = PhotoResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/api/listings/{listingId}/videos", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PhotoResponse add(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long listingId,
            @Valid @RequestBody AddListingVideoRequest body) {
        ListingVideo saved = listingVideoService.add(
                principal.userId(), listingId, body.url(), body.caption());
        return new PhotoResponse(saved.getId(), saved.getListingId(), saved.getUrl(),
                saved.getDisplayOrder(), saved.getCaption(), saved.getUploadedAt());
    }

    @Operation(summary = "Remove a listing video row (owner)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/listings/videos/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long videoId) {
        listingVideoService.delete(principal.userId(), videoId);
    }

    @Operation(summary = "List video URLs for a listing (public)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordered gallery; empty list is valid."),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements
    @GetMapping("/api/listings/{listingId}/videos")
    public List<PhotoResponse> list(@PathVariable Long listingId) {
        return listingVideoService.list(listingId).stream()
                .map(v -> new PhotoResponse(v.getId(), v.getListingId(), v.getUrl(),
                        v.getDisplayOrder(), v.getCaption(), v.getUploadedAt()))
                .toList();
    }
}
