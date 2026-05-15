package com.dreamhomes.haven.user;

import com.dreamhomes.haven.user.dto.PublicUserProfile;
import com.dreamhomes.haven.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public profile endpoint — no JWT required. Cached at the gateway / CDN layer via
 * {@code Cache-Control}, set by {@code PublicCacheHeadersInterceptor} (registered on
 * this path in {@code WebConfig}).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(
            summary = "Read a user's public profile",
            description = """
                    Returns the public-facing trust signals: name, role, verified-identity \
                    timestamp, agent-credential-verified timestamp (for agents), average \
                    review rating, and review count. **Never** returns email, phone, password \
                    hash, or `tokenVersion`.

                    Public — no JWT required. Response carries `Cache-Control: public, max-age=...` \
                    so a CDN or browser cache can serve repeat hits without round-tripping to \
                    Postgres. The badge fields update lazily — a freshly verified user may take \
                    one cache TTL to reflect their badge to anonymous viewers.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Public profile returned. Verified-* timestamps populated only when the user has been admin-approved.",
                    content = @Content(
                            schema = @Schema(implementation = PublicUserProfile.class),
                            examples = {
                                    @ExampleObject(name = "VerifiedAgent", value = """
                                            { "id": 23, "fullName": "Emeka Okonkwo", "role": "AGENT",
                                              "identityVerifiedAt": "2026-04-12T10:00:00Z",
                                              "agentCredentialVerifiedAt": "2026-04-13T11:00:00Z",
                                              "averageRating": 4.8, "reviewCount": 23, "suspended": false }
                                            """),
                                    @ExampleObject(name = "UnverifiedOwner", value = """
                                            { "id": 7, "fullName": "Amaka Okafor", "role": "OWNER",
                                              "identityVerifiedAt": null,
                                              "agentCredentialVerifiedAt": null,
                                              "averageRating": null, "reviewCount": 0, "suspended": false }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements // public — opt out of bearerAuth
    @GetMapping("/{id}/profile")
    public PublicUserProfile getPublicProfile(
            @Parameter(description = "User ID to look up.", example = "7")
            @PathVariable Long id) {
        PublicUserProfile profile = userProfileService.findPublicProfile(id);
        // Hide ADMIN accounts from public lookup — surfacing role=ADMIN at /users/1/profile
        // lets anyone enumerate admin user IDs (Temi flagged this in the persona audit).
        if (profile.role() == com.dreamhomes.haven.user.model.Role.ADMIN) {
            throw new com.dreamhomes.haven.user.exception.UserNotFoundException(id);
        }
        return profile;
    }
}
