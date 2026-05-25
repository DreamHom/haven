package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.photo.dto.PhotoConfirmRequest;
import com.dreamhomes.haven.photo.dto.PhotoResponse;
import com.dreamhomes.haven.photo.dto.PhotoUploadUrlRequest;
import com.dreamhomes.haven.photo.dto.PhotoUploadUrlResponse;
import com.dreamhomes.haven.photo.storage.PhotoStorage;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Listing photos")
public class ListingPhotoController {

    private final ListingPhotoService listingPhotoService;
    private final ListingPhotoMapper listingPhotoMapper;
    private final PhotoStorage photoStorage;
    private final ListingPhotoUploadIntentService uploadIntentService;

    @Operation(
            summary = "Upload a photo to a listing",
            description = """
                    Multipart upload. The `file` part is sent to the configured \
                    {@code PhotoStorage} backend (Cloudflare R2 in production via \
                    {@code haven.photos.storage=r2}; a no-bytes synthesised URL for \
                    dev / test). The hosted URL is then recorded against a new \
                    `ListingPhoto` row and `displayOrder` assigned server-side so the \
                    most recent upload appends to the end of the gallery.

                    **Ownership**: only the listing's owner can upload photos today. \
                    Assigned-agent upload is a likely future enhancement.

                    **Role gate**: `OWNER`.
                    """,
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = {
                                    @Encoding(name = "file", contentType = "image/jpeg, image/png, image/webp"),
                                    @Encoding(name = "caption", contentType = MediaType.TEXT_PLAIN_VALUE)
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Photo uploaded; `displayOrder` assigned by the server.",
                    content = @Content(
                            schema = @Schema(implementation = PhotoResponse.class),
                            examples = @ExampleObject(name = "AppendedPhoto", value = """
                                    { "id": 88, "listingId": 17,
                                      "url": "https://media.dreamhomes.com/listings/17/abc-uuid.jpg",
                                      "displayOrder": 3, "caption": "Living room",
                                      "uploadedAt": "2026-05-10T09:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/api/listings/{listingId}/photos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PhotoResponse add(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing to attach the photo to.", example = "17")
            @PathVariable Long listingId,
            @Parameter(description = "Image file (jpeg/png/webp).")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Optional human-readable caption.", required = false, example = "Living room")
            @RequestPart(value = "caption", required = false) String caption) {
        String hostedUrl = photoStorage.upload(file, listingId);
        return listingPhotoMapper.toResponse(listingPhotoService.add(
                principal.userId(), listingId, hostedUrl, caption));
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
                .map(listingPhotoMapper::toResponse).toList();
    }

    // ============================================================================
    // Item 2 — pre-signed R2 upload flow. Coexists with the legacy multipart endpoint
    // above; Vista migrates gradually. Both paths write the same `listing_photos` row.
    // ============================================================================

    @Operation(
            summary = "Mint a pre-signed R2 upload URL for a listing photo",
            description = """
                    Returns a single-use, 10-minute pre-signed HTTPS URL the browser PUTs \
                    image bytes to directly — bypassing Haven so a heavy upload doesn't \
                    consume API bandwidth. After the PUT succeeds, the client calls \
                    {@code POST /api/listings/{id}/photos/confirm} with the same {@code fileKey} \
                    to register the photo against the listing.

                    **Auth**: caller must be the listing owner OR an active assigned agent. \
                    Other authenticated users get 403; anonymous gets 401.

                    **Validation**:
                    - {@code contentType} must be one of `image/jpeg`, `image/png`, `image/webp`.
                    - {@code sizeBytes} must be > 0 and ≤ 10485760 (10 MB).

                    A `photo_upload_intent` row is persisted; cleanup of expired rows runs \
                    hourly. The companion `/confirm` endpoint validates against this row plus a \
                    HEAD request to R2 before writing `listings_photos`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Pre-signed URL minted. Browser should PUT bytes immediately.",
                    content = @Content(
                            schema = @Schema(implementation = PhotoUploadUrlResponse.class),
                            examples = @ExampleObject(name = "MintedUrl", value = """
                                    { "uploadUrl": "https://<account>.r2.cloudflarestorage.com/listings/17/abc-uuid-hero.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
                                      "fileKey": "listings/17/abc-uuid-hero.jpg",
                                      "expiresAt": "2026-05-24T10:00:00Z",
                                      "maxSizeBytes": 10485760,
                                      "allowedContentTypes": ["image/jpeg", "image/png", "image/webp"] }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid contentType or sizeBytes out of bounds.",
                    ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403",
                    description = "Caller is not the listing owner and not an active assigned agent.",
                    ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404",
                    description = "Listing doesn't exist.",
                    ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/api/listings/{listingId}/photos/upload-url",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT')")
    public PhotoUploadUrlResponse mintUploadUrl(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing to attach the photo to.", example = "17")
            @PathVariable Long listingId,
            @org.springframework.web.bind.annotation.RequestBody
            @Valid PhotoUploadUrlRequest request) {
        return uploadIntentService.createIntent(principal.userId(), listingId, request);
    }

    @Operation(
            summary = "Confirm a pre-signed upload landed",
            description = """
                    Server-side counterpart to the pre-signed PUT. Verifies the matching \
                    intent (issued to this caller, for this listing, not yet confirmed, not \
                    expired), HEADs R2 to confirm the bytes actually landed at the expected \
                    size, then writes a {@code listing_photos} row with a server-assigned \
                    {@code displayOrder}.

                    **Auth**: same as mint — listing owner OR active assigned agent.

                    **Error semantics**:
                    - 409 — `fileKey` was never issued, already confirmed, or expired.
                    - 422 — R2 reports the object is missing OR its size doesn't match \
                      {@code sizeBytes}.

                    The {@code width} / {@code height} fields are accepted but not validated \
                    against R2 metadata (R2 wouldn't carry them on PUT); they are stored on \
                    the photo row for the gallery UI's responsive-image heuristics.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Photo registered; gallery shows it at the assigned displayOrder.",
                    content = @Content(
                            schema = @Schema(implementation = PhotoResponse.class),
                            examples = @ExampleObject(name = "Registered", value = """
                                    { "id": 88, "listingId": 17,
                                      "url": "https://media.dreamhomes.com/listings/17/abc-uuid-hero.jpg",
                                      "displayOrder": 3, "caption": "Living room facing the lagoon",
                                      "uploadedAt": "2026-05-10T09:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409",
                    description = "fileKey was never issued, already confirmed, or expired.",
                    ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422",
                    description = "Object missing in R2 OR size mismatch vs claimed sizeBytes.")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/api/listings/{listingId}/photos/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT')")
    public PhotoResponse confirmUpload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing to attach the photo to.", example = "17")
            @PathVariable Long listingId,
            @org.springframework.web.bind.annotation.RequestBody
            @Valid PhotoConfirmRequest request) {
        return listingPhotoMapper.toResponse(
                uploadIntentService.confirm(principal.userId(), listingId, request));
    }

}
