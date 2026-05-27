package com.dreamhomes.haven.agentmarketing;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.agentmarketing.dto.AgentMarketingMediaResponse;
import com.dreamhomes.haven.agentmarketing.dto.ReorderAgentMarketingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/me/agent-marketing")
@RequiredArgsConstructor
@Tag(name = "Agent marketing gallery")
public class AgentMarketingMediaController {

    private final AgentMarketingMediaService agentMarketingMediaService;

    @GetMapping
    @PreAuthorize("hasRole('AGENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List your marketing gallery items",
            description = """
                    **Persona**: Emeka (S5) — surfaces his uploaded sample work on his agent profile.

                    Returns every gallery item the calling agent owns, ordered by `displayOrder` ascending.
                    Embedded on the agent profile read for owners + applicants browsing for an agent.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gallery items the caller owns (may be empty)."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    public List<AgentMarketingMediaResponse> list(@AuthenticationPrincipal JwtPrincipal principal) {
        return agentMarketingMediaService.listMine(principal.userId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Upload a marketing gallery image",
            description = """
                    **Persona**: Emeka (S5) — adds a photo of past work to build social proof.

                    `multipart/form-data` with field `file` (image/jpeg, image/png, image/webp; max ~5MB)
                    and optional `caption` field. Server proxies the upload to R2 and stamps a fresh
                    `displayOrder` at the end of the agent's current gallery. Public profile cache is
                    evicted so the new item appears on next profile fetch.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Uploaded; returns the new gallery item."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    public AgentMarketingMediaResponse upload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption) {
        return agentMarketingMediaService.upload(principal.userId(), file, caption);
    }

    @PatchMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('AGENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Set gallery display order (every item id exactly once)",
            description = """
                    **Persona**: Emeka (S5) — re-arranges his portfolio to put the best work first.

                    Body must list every item the agent currently owns, exactly once, in the desired
                    order. Server validates the list is a complete permutation before applying; a
                    missing or unknown id fails with 400 / 409. Cache for the agent's public profile
                    is evicted on success.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Order applied."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public void reorder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ReorderAgentMarketingRequest body) {
        agentMarketingMediaService.reorderMine(principal.userId(), body.mediaIds());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('AGENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Remove a marketing gallery item",
            description = """
                    **Persona**: Emeka (S5) — drops an item that's no longer representative.

                    Deletes the item if it belongs to the caller. Subsequent items keep their existing
                    `displayOrder` values (no auto-renumbering). Public profile cache evicted.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        agentMarketingMediaService.deleteMine(principal.userId(), id);
    }
}
