package com.dreamhomes.haven.engagement;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.engagement.model.ListingSave;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Saves")
public class ListingSaveController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ListingSaveService listingSaveService;

    @Operation(
            summary = "Save a listing",
            description = """
                    Bookmarks the listing for the calling user. **Idempotent** — saving \
                    the same listing twice succeeds (the existing row is left in place). \
                    The save shows up under `GET /saves/mine` and feeds the listing's \
                    aggregated engagement signal.

                    Returns 204 with no body either way.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Saved (or already saved — idempotent)."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/listings/{listingId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing ID to save.", example = "17")
            @PathVariable Long listingId) {
        listingSaveService.save(principal.userId(), listingId);
    }

    @Operation(
            summary = "Un-save a listing",
            description = """
                    Removes the save row for the calling user against the target listing. \
                    Idempotent — un-saving something you didn't save returns 204 with no \
                    error.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Un-saved (or wasn't saved — idempotent)."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/listings/{listingId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing ID to un-save.", example = "17")
            @PathVariable Long listingId) {
        listingSaveService.unsave(principal.userId(), listingId);
    }

    @Operation(
            summary = "List my saved listings",
            description = """
                    Paginated list of the calling user's `ListingSave` rows. Private — \
                    scoped to the JWT subject; nobody else can read another user's saves \
                    via this endpoint.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated saves. Empty page is valid for a fresh account.",
                    content = @Content(
                            examples = @ExampleObject(name = "TwoSaves", value = """
                                    { "content": [
                                        { "userId": 89, "listingId": 17, "savedAt": "2026-05-09T18:30:00Z" },
                                        { "userId": 89, "listingId": 23, "savedAt": "2026-05-09T19:15:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 2, "totalPages": 1 } }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/saves/mine")
    public Page<ListingSave> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return listingSaveService.listMine(principal.userId(), pageable);
    }
}
