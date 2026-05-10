package com.dreamhomes.haven.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.dreamhomes.haven.auth.dto.LoginCommand;
import com.dreamhomes.haven.auth.dto.LoginRequest;
import com.dreamhomes.haven.auth.dto.LoginResponse;
import com.dreamhomes.haven.auth.dto.RegisterCommand;
import com.dreamhomes.haven.auth.dto.RegisterRequest;
import com.dreamhomes.haven.auth.dto.UserResponse;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(new RegisterCommand(
                request.email(), request.password(), request.fullName(), request.phone(),
                request.role(), request.licenseNumber()));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(new LoginCommand(request.email(), request.password())));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtPrincipal principal) {
        authService.logout(principal.userId());
    }
}
