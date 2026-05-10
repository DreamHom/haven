package com.dreamhomes.haven.auth.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny convenience endpoint — returns the authenticated principal as-is. Lives in the
 * auth feature now (used to be a one-class {@code me/} package); nothing else conceptually
 * lives there and the route is fundamentally about identity.
 */
@RestController
@Tag(name = "Auth")
public class MeController {

    @Operation(
            summary = "Identify the current authenticated user",
            description = """
                    Returns the JWT-derived principal — the same shape Spring Security holds \
                    in its security context. Useful for the frontend on app boot to confirm \
                    a stored JWT is still valid and to display the user's role + name.

                    No DB read happens here; the response is reconstructed from the JWT's \
                    own claims, so the call is O(1).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Authenticated principal.",
                    content = @Content(
                            schema = @Schema(implementation = JwtPrincipal.class),
                            examples = @ExampleObject(name = "OwnerPrincipal", value = """
                                    { "userId": 7, "email": "amaka@gmail.com",
                                      "fullName": "Amaka Okafor", "role": "OWNER" }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/me")
    public JwtPrincipal me(@AuthenticationPrincipal JwtPrincipal principal) {
        return principal;
    }
}
