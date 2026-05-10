package com.dreamhomes.haven.support;

import com.dreamhomes.haven.auth.JwtService;

import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test helper for the auth slice. Provides one-liner ways for protected-endpoint ITs to
 * obtain a valid bearer token without re-implementing the register/login dance every time.
 *
 * <p>Usage:
 * <pre>{@code
 * @Autowired JwtTestSupport jwt;
 *
 * String bearer = jwt.bearerFor(jwt.persistUser(Role.OWNER));
 * mockMvc.perform(get("/api/listings/mine").header("Authorization", bearer))
 *        .andExpect(status().isOk());
 * }</pre>
 *
 * <p>Lives in main test sources (not under @TestConfiguration) so it is auto-discovered
 * via component scan in any test that brings up the full Spring context.
 */
@Component
public class JwtTestSupport {

    private static final AtomicLong COUNTER = new AtomicLong();
    public static final String DEFAULT_PASSWORD = "test-password-123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Autowired
    public JwtTestSupport(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User persistUser(Role role) {
        long n = COUNTER.incrementAndGet();
        return userRepository.save(User.builder()
                .email("test-" + role.name().toLowerCase() + "-" + n + "@example.com")
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(role)
                .fullName("Test " + role.name())
                .tokenVersion(1)
                .createdAt(Instant.now())
                .build());
    }

    public String tokenFor(User user) {
        return jwtService.issue(user.getId(), user.getEmail(), user.getRole(), user.getTokenVersion());
    }

    public String bearerFor(User user) {
        return "Bearer " + tokenFor(user);
    }
}
