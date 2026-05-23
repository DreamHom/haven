package com.dreamhomes.haven.auth.passwordreset;

import com.dreamhomes.haven.auth.exception.InvalidResetTokenException;
import com.dreamhomes.haven.auth.passwordreset.model.PasswordResetToken;
import com.dreamhomes.haven.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${haven.auth.debug-return-reset-token:false}")
    private boolean debugReturnResetToken;

    /**
     * Always succeeds from a caller-observable perspective. When a matching active user
     * exists, persists a single-use token (hashed at rest).
     *
     * @return raw token for debug builds only; empty when user missing or debug disabled.
     */
    @Transactional
    public Optional<String> requestReset(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> user = userRepository.findByEmailAndAccountDeletedAtIsNull(normalized);
        if (user.isEmpty()) {
            log.info("Forgot-password noop for email='{}' (no active user)", normalized);
            return Optional.empty();
        }
        String raw = newToken();
        Instant now = Instant.now();
        tokenRepository.save(PasswordResetToken.builder()
                .userId(user.get().getId())
                .tokenHash(sha256Hex(raw))
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .createdAt(now)
                .build());
        log.info("Issued password reset token for userId={}", user.get().getId());
        if (debugReturnResetToken) {
            log.warn(
                    "haven.auth.debug-return-reset-token=true — password reset token for userId={} "
                            + "(same value as response debugResetToken): {}",
                    user.get().getId(),
                    raw);
        }
        return debugReturnResetToken ? Optional.of(raw) : Optional.empty();
    }

    @Transactional
    public void resetWithToken(String rawToken, String newPassword) {
        String hash = sha256Hex(rawToken.trim());
        Instant now = Instant.now();
        PasswordResetToken row = tokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(hash, now)
                .orElseThrow(InvalidResetTokenException::new);
        User user = userRepository.findById(row.getUserId())
                .filter(u -> u.getAccountDeletedAt() == null)
                .orElseThrow(InvalidResetTokenException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        row.setUsedAt(now);
        tokenRepository.save(row);
        log.info("Password reset consumed for userId={}", user.getId());
    }

    private static String newToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
