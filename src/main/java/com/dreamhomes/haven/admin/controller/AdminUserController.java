package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.user.dto.UserAdminView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dreamhomes.haven.admin.dto.SuspendUserRequest;
import com.dreamhomes.haven.admin.service.AdminUserService;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/{id}/suspend")
    public UserAdminView suspend(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SuspendUserRequest request) {
        return adminUserService.suspend(principal.userId(), id, request.reason());
    }

    @PostMapping("/{id}/reactivate")
    public UserAdminView reactivate(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        return adminUserService.reactivate(principal.userId(), id);
    }
}
