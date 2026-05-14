package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.comment.CommentFlagService;
import com.dreamhomes.haven.comment.CommentFlagStatus;
import com.dreamhomes.haven.comment.dto.CommentFlagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/comment-flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminCommentFlagController {

    private final CommentFlagService commentFlagService;

    @Operation(summary = "List comment moderation flags",
            description = "Paginated queue. Optional `status` filter (`OPEN`, `RESOLVED`, `DISMISSED`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated flags."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public Page<CommentFlagResponse> list(
            @Parameter(description = "Optional status filter.")
            @RequestParam(required = false) CommentFlagStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return commentFlagService.adminList(status, pageable);
    }

    @Operation(summary = "Resolve a comment flag", description = "Marks an `OPEN` flag as `RESOLVED`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag updated."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/resolve")
    public CommentFlagResponse resolve(
            @Parameter(description = "Flag id.", example = "12")
            @PathVariable Long id) {
        return commentFlagService.resolve(id);
    }

    @Operation(summary = "Dismiss a comment flag", description = "Marks an `OPEN` flag as `DISMISSED`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag updated."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/dismiss")
    public CommentFlagResponse dismiss(
            @Parameter(description = "Flag id.", example = "12")
            @PathVariable Long id) {
        return commentFlagService.dismiss(id);
    }
}
