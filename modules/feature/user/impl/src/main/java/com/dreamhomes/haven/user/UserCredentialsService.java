package com.dreamhomes.haven.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Owns the credential-flow writes for the user feature. Replaces what was previously
 * direct {@code UserRepository} access from {@code feature/auth/impl}.
 *
 * <p>Auth-impl no longer sees {@link User} or {@link UserRepository} — it talks to
 * this service via {@link UserCredentialsApi} only. The 1:1 mapping between repo
 * methods and api methods is intentional: this is the smallest possible facade.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserCredentialsService implements UserCredentialsApi {

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserCredentials> loadByEmail(String email) {
        return userRepository.findByEmail(email).map(UserCredentialsService::toCredentials);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalInt tokenVersionOf(Long userId) {
        return userRepository.findById(userId)
                .map(u -> OptionalInt.of(u.getTokenVersion()))
                .orElseGet(OptionalInt::empty);
    }

    @Override
    @Transactional
    public OptionalInt bumpTokenVersion(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    int next = user.getTokenVersion() + 1;
                    user.setTokenVersion(next);
                    userRepository.save(user);
                    log.info("Bumped tokenVersion for userId={} to {}", userId, next);
                    return OptionalInt.of(next);
                })
                .orElseGet(() -> {
                    log.warn("bumpTokenVersion called for missing userId={}", userId);
                    return OptionalInt.empty();
                });
    }

    @Override
    @Transactional
    public RegisteredUser create(NewUser newUser) {
        if (userRepository.existsByEmail(newUser.email())) {
            throw new EmailAlreadyTakenException();
        }
        Instant now = Instant.now();
        User user = User.builder()
                .email(newUser.email())
                .passwordHash(newUser.passwordHash())
                .role(newUser.role())
                .fullName(newUser.fullName())
                .phone(newUser.phone())
                .createdAt(now)
                .build();
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException race) {
            // Lost a TOCTOU race against a concurrent insert with the same email.
            // The DB UNIQUE constraint already blocked the duplicate; surface the same
            // 409 as the pre-check path so the wire response stays uniform.
            throw new EmailAlreadyTakenException();
        }

        if (saved.getRole() == Role.AGENT) {
            agentProfileRepository.save(AgentProfile.builder()
                    .userId(saved.getId())
                    .licenseNumber(newUser.licenseNumber())
                    .createdAt(now)
                    .build());
        }

        log.info("Created userId={} role={}", saved.getId(), saved.getRole());
        return new RegisteredUser(saved.getId(), saved.getCreatedAt());
    }

    private static UserCredentials toCredentials(User user) {
        return new UserCredentials(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole(),
                user.getTokenVersion(),
                user.getSuspendedAt() != null);
    }
}
