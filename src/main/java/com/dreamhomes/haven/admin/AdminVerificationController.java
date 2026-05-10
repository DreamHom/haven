package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.verification.VerificationAdminView;
import com.dreamhomes.haven.verification.VerificationType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin queue + decision endpoints. Whole controller is gated by {@code @PreAuthorize}
 * at the class level — non-admins get 403 before any handler runs.
 */
@RestController
@RequestMapping("/api/admin/verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminVerificationService adminVerificationService;

    @GetMapping
    public Page<VerificationAdminView> listPending(
            @RequestParam VerificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return adminVerificationService.listPending(type, pageable);
    }

    @PostMapping("/{id}/approve")
    public VerificationAdminView approve(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        return adminVerificationService.approve(principal.userId(), id, null);
    }

    @PostMapping("/{id}/reject")
    public VerificationAdminView reject(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RejectVerificationRequest request) {
        return adminVerificationService.reject(principal.userId(), id, request.reason());
    }
}
