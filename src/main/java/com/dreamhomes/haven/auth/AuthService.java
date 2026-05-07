package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.AgentProfile;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    /**
     * A real BCrypt hash of a fixed input, computed once at class load. When a login
     * is attempted for a missing email we still run {@code matches} against this hash
     * so wall-clock cost is the same as for an existing user with a wrong password —
     * preventing email enumeration via response timing. Computed (not hard-coded) so
     * the hash always satisfies BCrypt's pattern check and reaches the constant-time
     * compare path inside {@link BCryptPasswordEncoder}.
     */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("never-matches");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AgentProfileRepository agentProfileRepository;

    @Transactional(readOnly = true)
    public String login(LoginCommand cmd) {
        String email = normalize(cmd.email());
        User user = userRepository.findByEmail(email).orElse(null);
        String hashToCheck = user != null ? user.getPasswordHash() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(cmd.password(), hashToCheck);
        if (user == null || !passwordMatches) {
            log.warn("Login failed for email='{}'", email);
            throw new InvalidCredentialsException();
        }
        log.info("Login succeeded for userId={} role={}", user.getId(), user.getRole());
        return jwtService.issue(user.getId(), user.getEmail(), user.getRole(), user.getTokenVersion());
    }

    /**
     * Bumps the user's {@code tokenVersion} so every previously-issued JWT for this user
     * is rejected by the auth filter on the next request. Idempotent: calling twice in a
     * row produces tokens-already-invalid, which is fine.
     */
    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
            log.info("Logged out userId={}, bumped tokenVersion to {}", userId, user.getTokenVersion());
        });
    }

    @Transactional
    public User register(RegisterCommand cmd) {
        String email = normalize(cmd.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(cmd.password()))
                .role(cmd.role())
                .fullName(cmd.fullName())
                .phone(cmd.phone())
                .createdAt(Instant.now())
                .build();
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException race) {
            // Lost a TOCTOU race against a concurrent registration with the same email.
            // The DB UNIQUE constraint already blocked the dup; surface the same 409 as the
            // pre-check path so the client sees one consistent failure mode.
            throw new EmailAlreadyRegisteredException();
        }

        if (saved.getRole() == Role.AGENT) {
            agentProfileRepository.save(AgentProfile.builder()
                    .userId(saved.getId())
                    .licenseNumber(cmd.licenseNumber())
                    .createdAt(Instant.now())
                    .build());
        }

        log.info("Registered userId={} role={}", saved.getId(), saved.getRole());
        return saved;
    }

    /** Emails are case-insensitive identifiers — store and look them up in lowercase. */
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
