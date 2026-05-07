package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for the agent–listing assignment handshake.
 *
 * <p>Three role-gated POSTs and a paginated GET for the caller's own assignments. The
 * controller delegates authorisation to the service for the multi-condition checks
 * (the simple role gate goes through {@code @PreAuthorize}; the "is this caller the
 * targeted agent / listing owner / admin" check lives in the service alongside the
 * state-machine logic).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentListingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AgentListingService agentListingService;

    @PostMapping("/listings/{listingId}/agent-assignment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public AgentListingResponse request(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long listingId,
            @Valid @RequestBody RequestAgentAssignmentRequest request) {
        return AgentListingResponse.from(
                agentListingService.request(principal.userId(), listingId, request.agentId()));
    }

    @PostMapping("/agent-listings/{id}/accept")
    @PreAuthorize("hasRole('AGENT')")
    public AgentListingResponse accept(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        return AgentListingResponse.from(
                agentListingService.respond(principal.userId(), id, AgentListingStatus.ACCEPTED, null));
    }

    @PostMapping("/agent-listings/{id}/decline")
    @PreAuthorize("hasRole('AGENT')")
    public AgentListingResponse decline(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DeclineAssignmentRequest request) {
        return AgentListingResponse.from(
                agentListingService.respond(principal.userId(), id, AgentListingStatus.DECLINED, request.reason()));
    }

    @PostMapping("/agent-listings/{id}/revoke")
    public AgentListingResponse revoke(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RevokeAssignmentRequest request) {
        return AgentListingResponse.from(
                agentListingService.revoke(principal.userId(), principal.role(), id, request.reason()));
    }

    @GetMapping("/agent-listings/mine")
    public Page<AgentListingResponse> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return agentListingService.listMine(principal.userId(), principal.role(), pageable)
                .map(AgentListingResponse::from);
    }
}
