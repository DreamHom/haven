package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dream-ai")
@RequiredArgsConstructor
@Tag(name = "Dream AI")
public class DreamAiController {

    private final DreamAiService dreamAiService;

    @Operation(
            summary = "Suggest listings from a natural-language prompt",
            description = """
                    **Stub implementation** for Vista wiring: runs the public browse pipeline \
                    with the trimmed prompt as the `location` address fragment (same semantics \
                    as `GET /api/listings?location=`). No external LLM call.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordered listing id suggestions."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/suggestions")
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT', 'ADMIN')")
    public DreamAiSuggestionResponse suggestions(@Valid @RequestBody DreamAiSuggestionRequest request) {
        return dreamAiService.suggest(request);
    }
}
