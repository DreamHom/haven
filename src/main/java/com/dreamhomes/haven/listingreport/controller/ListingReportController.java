package com.dreamhomes.haven.listingreport.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.listingreport.dto.ListingReportResponse;
import com.dreamhomes.haven.listingreport.dto.ReportListingCommand;
import com.dreamhomes.haven.listingreport.dto.ReportListingRequest;
import com.dreamhomes.haven.listingreport.model.ListingReport;
import com.dreamhomes.haven.listingreport.service.ListingReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequiredArgsConstructor
@Tag(name = "Listings")
public class ListingReportController {

    private final ListingReportService listingReportService;

    @Operation(
            summary = "Report a listing",
            description = """
                    Files a moderation report against a listing. Any authenticated user can \
                    submit. The report is recorded against the caller and a \
                    `LISTING_REPORTED` notification is fanned out to every admin so the \
                    moderation queue surfaces it without polling.

                    A user can only file one report per listing — duplicate attempts return \
                    409. The DB unique index `listing_reports_one_per_user_per_listing` \
                    enforces this even if the application-side check is skipped.

                    Self-reports are allowed: an owner whose listing was hijacked can flag \
                    their own row.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Report recorded.",
                    content = @Content(
                            schema = @Schema(implementation = ListingReportResponse.class),
                            examples = @ExampleObject(name = "ScamReport", value = """
                                    { "id": 17, "listingId": 423, "reason": "SCAM",
                                      "details": "Asking for ₦200k 'inspection fee' off-platform",
                                      "createdAt": "2026-05-10T08:30:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/listings/{listingId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingReportResponse report(@AuthenticationPrincipal JwtPrincipal principal,
                                        @PathVariable Long listingId,
                                        @Valid @RequestBody ReportListingRequest request) {
        ListingReport saved = listingReportService.report(principal.userId(), listingId,
                new ReportListingCommand(request.reason(), request.details()));
        return new ListingReportResponse(saved.getId(), saved.getListingId(),
                saved.getReason(), saved.getDetails(), saved.getCreatedAt());
    }
}
