package com.dreamhomes.haven.domain.admin.controller;

import com.dreamhomes.haven.domain.admin.service.AdminListingService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/listings")
public class AdminListingController {
    private final AdminListingService adminListingService;

    public AdminListingController(AdminListingService adminListingService) {
        this.adminListingService = adminListingService;
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable Long id) {
        adminListingService.approveListing(id);
    }
}

