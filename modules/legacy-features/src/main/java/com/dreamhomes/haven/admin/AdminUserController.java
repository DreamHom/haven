package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/{id}/suspend")
    public AdminUserResponse suspend(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SuspendUserRequest request) {
        User suspended = adminUserService.suspend(principal.userId(), id, request.reason());
        return AdminUserResponse.from(suspended);
    }

    @PostMapping("/{id}/reactivate")
    public AdminUserResponse reactivate(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        User reactivated = adminUserService.reactivate(principal.userId(), id);
        return AdminUserResponse.from(reactivated);
    }
}
