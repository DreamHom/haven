package com.dreamhomes.haven.user;

import com.dreamhomes.haven.user.dto.PublicUserProfile;
import com.dreamhomes.haven.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public agent directory. Owners (and applicants doing trust diligence) can search
 * AGENT-role users by name and filter to identity-verified only. Persona audit
 * (Biodun): "delegation-first product where the owner cannot find an agent to
 * delegate to is broken at the design level."
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Tag(name = "Users")
public class AgentDirectoryController {

    private final UserProfileService userProfileService;

    @Operation(
            summary = "Search verified agents",
            description = """
                    Returns AGENT-role users (non-suspended), sorted with verified agents
                    first. Pass `?q=Emeka` to substring-match on `fullName`/`displayName`,
                    `?verified=true` to restrict to badge-stamped agents.

                    Public — no auth required. Verified agents surface first so owners
                    looking to delegate see the trustworthy ones immediately.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of agent profiles.")
    })
    @SecurityRequirements // public
    @GetMapping
    public Page<PublicUserProfile> search(
            @Parameter(description = "Substring of name (case-insensitive). Empty / omitted = no filter.")
            // defaultValue="" rather than required=false: a Java null arrives at the JPQL
            // as an untyped JDBC bind, which Postgres coerces to bytea and then fails to
            // resolve LOWER(bytea). Always handing the repo a real String dodges that —
            // empty string flows through the LIKE '%%' branch which matches all rows,
            // i.e. the intended "no filter" behaviour.
            @RequestParam(value = "q", required = false, defaultValue = "") String q,
            @Parameter(description = "If true, only return identity-verified agents.")
            @RequestParam(name = "verified", defaultValue = "false") boolean verified,
            @PageableDefault(size = 20) Pageable pageable) {
        return userProfileService.searchAgents(q, verified, pageable);
    }
}
