package com.dreamhomes.haven.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.OptionalInt;
import com.dreamhomes.haven.user.dto.NewUser;
import com.dreamhomes.haven.user.dto.RegisteredUser;
import com.dreamhomes.haven.user.dto.UserCredentials;
import com.dreamhomes.haven.user.exception.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.mapping.UserCredentialsMapper;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
/**
 * Owns the credential-flow writes for the user feature. Replaces what was previously
 * direct {@code UserRepository} access from {@code feature/auth/impl}.
 *
 * <p>Auth-impl no longer sees {@link User} or {@link UserRepository} — it talks to
 * this service via {@link UserCredentialsService} only. The 1:1 mapping between repo
 * methods and api methods is intentional: this is the smallest possible facade.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserCredentialsService {

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final UserCredentialsMapper userCredentialsMapper;

    @Transactional(readOnly = true)
    public Optional<UserCredentials> loadByEmail(String email) {
        return userRepository.findByEmailAndAccountDeletedAtIsNull(email)
                .map(userCredentialsMapper::toCredentials);
    }

    @Transactional(readOnly = true)
    public Optional<UserCredentials> loadById(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getAccountDeletedAt() == null)
                .map(userCredentialsMapper::toCredentials);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailAndAccountDeletedAtIsNull(email);
    }

    @Transactional(readOnly = true)
    public OptionalInt tokenVersionOf(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getAccountDeletedAt() == null)
                .map(u -> OptionalInt.of(u.getTokenVersion()))
                .orElseGet(OptionalInt::empty);
    }

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

    @Transactional
    public RegisteredUser create(NewUser newUser) {
        if (userRepository.existsByEmailAndAccountDeletedAtIsNull(newUser.email())) {
            throw new EmailAlreadyTakenException();
        }
        User user = User.builder()
                .email(newUser.email())
                .passwordHash(newUser.passwordHash())
                .role(newUser.role())
                .fullName(newUser.fullName())
                .displayName(newUser.displayName())
                .phone(newUser.phone())
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
                    .build());
        }

        log.info("Created userId={} role={}", saved.getId(), saved.getRole());
        return new RegisteredUser(saved.getId(), saved.getCreatedAt());
    }

}
