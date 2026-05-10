package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.photo.dto.AddPhotoRequest;
import com.dreamhomes.haven.photo.dto.PhotoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Listing photos")
public class ListingPhotoController {

    private final ListingPhotoService listingPhotoService;

    @Operation(
            summary = "Attach a photo to a listing",
            description = """
                    Records a `ListingPhoto` row pointing at an externally-hosted image URL \
                    (R2-uploaded image when the upload pipeline lands; for now, any URL the \
                    caller supplies). Server assigns the next `displayOrder` so the most \
                    recent photo is appended at the end of the gallery.

                    **Ownership**: only the listing's owner can attach photos today. \
                    Assigned-agent upload is a likely future enhancement.

                    **Role gate**: `OWNER`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Photo recorded; `displayOrder` assigned by the server.",
                    content = @Content(
                            schema = @Schema(implementation = PhotoResponse.class),
                            examples = @ExampleObject(name = "AppendedPhoto", value = """
                                    { "id": 88, "listingId": 17,
                                      "url": "https://media.dreamhomes.com/listings/17/hero.jpg",
                                      "displayOrder": 3, "caption": "Living room",
                                      "uploadedAt": "2026-05-10T09:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/listings/{listingId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PhotoResponse add(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing to attach the photo to.", example = "17")
            @PathVariable Long listingId,
            @Valid @RequestBody AddPhotoRequest request) {
        return toResponse(listingPhotoService.add(
                principal.userId(), listingId, request.url(), request.caption()));
    }

    @Operation(
            summary = "Remove a photo from a listing",
            description = """
                    Hard-deletes the `ListingPhoto` row. Surrounding photos keep their \
                    existing `displayOrder` values — gaps are tolerated, the gallery just \
                    skips them.

                    **Ownership**: only the listing's owner can delete photos.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Photo removed."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/listings/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Photo ID.", example = "88")
            @PathVariable Long photoId) {
        listingPhotoService.delete(principal.userId(), photoId);
    }

    @Operation(
            summary = "List the photos for a listing",
            description = """
                    Returns photos in `displayOrder` ascending. Public — no auth required. \
                    Cache-Control headers stamped by the public-cache interceptor; CDN can \
                    serve repeat hits cheaply.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Photos in display order. Empty list is valid.",
                    content = @Content(
                            examples = @ExampleObject(name = "ThreePhotos", value = """
                                    [
                                      { "id": 86, "listingId": 17,
                                        "url": "https://media.dreamhomes.com/listings/17/exterior.jpg",
                                        "displayOrder": 0, "caption": "Building exterior",
                                        "uploadedAt": "2026-05-09T20:00:00Z" },
                                      { "id": 87, "listingId": 17,
                                        "url": "https://media.dreamhomes.com/listings/17/kitchen.jpg",
                                        "displayOrder": 1, "caption": "Kitchen",
                                        "uploadedAt": "2026-05-09T20:01:00Z" },
                                      { "id": 88, "listingId": 17,
                                        "url": "https://media.dreamhomes.com/listings/17/hero.jpg",
                                        "displayOrder": 2, "caption": "Living room",
                                        "uploadedAt": "2026-05-10T09:00:00Z" }
                                    ]
                                    """))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements // public
    @GetMapping("/api/listings/{listingId}/photos")
    public List<PhotoResponse> list(
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long listingId) {
        return listingPhotoService.list(listingId).stream()
                .map(ListingPhotoController::toResponse).toList();
    }

    static PhotoResponse toResponse(ListingPhoto p) {
        return new PhotoResponse(p.getId(), p.getListingId(), p.getUrl(),
                p.getDisplayOrder(), p.getCaption(), p.getUploadedAt());
    }
}
