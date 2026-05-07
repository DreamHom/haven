package com.dreamhomes.haven.domain.user.controller;

import com.dreamhomes.haven.domain.user.dto.AuthResponse;
import com.dreamhomes.haven.domain.user.dto.LoginRequest;
import com.dreamhomes.haven.domain.user.dto.RegisterRequest;
import com.dreamhomes.haven.domain.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        authService.authenticate(req);
        return new AuthResponse("TODO", "Bearer");
    }
}

