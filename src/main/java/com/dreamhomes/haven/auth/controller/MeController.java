package com.dreamhomes.haven.auth.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.dto.MeResponse;
import com.dreamhomes.haven.user.dto.UserCredentials;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny convenience endpoint — returns the authenticated user's identity. Used
 * by the frontend on app boot to confirm a stored JWT is still valid and to
 * display the user's name + role.
 *
 * <p>The response is a {@link MeResponse} (not the raw {@link JwtPrincipal})
 * so internal fields like {@code tokenVersion} don't leak to clients, and
 * {@code fullName} is included for greet-by-name UX.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth")
public class MeController {

    private final UserCredentialsService userCredentialsService;

    @Operation(
            summary = "Identify the current authenticated user",
            description = """
                    Returns the authenticated user's id, email, name, and role. Useful for the \
                    frontend on app boot to confirm a stored JWT is still valid and to display \
                    the user's role + name without a second profile call.

                    Does one DB read to pull `fullName`; not a hot-path call (frontend caches \
                    the response for the session).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Authenticated user identity.",
                    content = @Content(
                            schema = @Schema(implementation = MeResponse.class),
                            examples = @ExampleObject(name = "OwnerIdentity", value = """
                                    { "userId": 7, "email": "amaka@gmail.com",
                                      "fullName": "Amaka Okafor", "role": "OWNER" }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        UserCredentials creds = userCredentialsService.loadById(principal.userId())
                .orElseThrow(() -> new UserNotFoundException(principal.userId()));
        return new MeResponse(creds.id(), creds.email(), creds.fullName(), creds.role());
    }
}
