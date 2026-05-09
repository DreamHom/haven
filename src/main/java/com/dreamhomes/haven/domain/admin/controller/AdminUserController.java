package com.dreamhomes.haven.domain.admin.controller;

import com.dreamhomes.haven.domain.admin.dto.UserModerationRequest;
import com.dreamhomes.haven.domain.admin.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    @PostMapping("/{id}/moderate")
    public void moderate(@PathVariable Long id, @Valid @RequestBody UserModerationRequest req) {
        adminUserService.moderateUser(id, req);
    }
}

