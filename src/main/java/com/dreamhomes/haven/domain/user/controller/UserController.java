package com.dreamhomes.haven.domain.user.controller;

import com.dreamhomes.haven.domain.user.dto.UpdateProfileRequest;
import com.dreamhomes.haven.domain.user.dto.UserResponse;
import com.dreamhomes.haven.domain.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        var u = userService.getById(id);
        return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.getFirstName(), u.getLastName(), u.getDisplayName());
    }

    @PutMapping("/{id}/profile")
    public UserResponse updateProfile(@PathVariable Long id, @Valid @RequestBody UpdateProfileRequest req) {
        var u = userService.updateProfile(id, req);
        return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.getFirstName(), u.getLastName(), u.getDisplayName());
    }
}

