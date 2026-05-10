package com.dreamhomes.haven.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dreamhomes.haven.auth.JwtPrincipal;

/**
 * Tiny convenience endpoint — returns the authenticated principal as-is. Lives in the
 * auth feature now (used to be a one-class {@code me/} package); nothing else conceptually
 * lives there and the route is fundamentally about identity.
 */
@RestController
public class MeController {

    @GetMapping("/api/me")
    public JwtPrincipal me(@AuthenticationPrincipal JwtPrincipal principal) {
        return principal;
    }
}
