package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Submission endpoint for the four verification tracks. Admin queue + decision endpoints
 * live under {@code /api/admin/...} — see the admin package.
 *
 * <p>{@code @PreAuthorize} denies the four well-known non-admin roles plus blocks ADMIN
 * from self-submitting; the role-vs-track consistency check (only OWNERS submit
 * OWNER_IDENTITY etc.) lives in the service.
 */
@RestController
@RequestMapping("/api/verifications")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT')")
    public VerificationResponse submit(@AuthenticationPrincipal JwtPrincipal principal,
                                       @Valid @RequestBody SubmitVerificationRequest request) {
        return VerificationResponse.from(
                verificationService.submit(principal.userId(), request.toCommand()));
    }
}
