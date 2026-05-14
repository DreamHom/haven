package com.dreamhomes.haven.agentmarketing;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.agentmarketing.dto.AgentMarketingMediaResponse;
import com.dreamhomes.haven.agentmarketing.dto.ReorderAgentMarketingRequest;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "List your marketing gallery items")
    public List<AgentMarketingMediaResponse> list(@AuthenticationPrincipal JwtPrincipal principal) {
        return agentMarketingMediaService.listMine(principal.userId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload a marketing gallery image")
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
    @Operation(summary = "Set gallery display order (every item id exactly once)")
    public void reorder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ReorderAgentMarketingRequest body) {
        agentMarketingMediaService.reorderMine(principal.userId(), body.mediaIds());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('AGENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Remove a marketing gallery item")
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        agentMarketingMediaService.deleteMine(principal.userId(), id);
    }
}
