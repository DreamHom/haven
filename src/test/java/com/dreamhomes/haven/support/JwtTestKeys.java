package com.dreamhomes.haven.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads the fixed test RSA keypair shipped under {@code src/test/resources/jwt/} so every
 * integration test can wire {@code haven.jwt.private-key} and {@code haven.jwt.public-key}
 * without each test re-deriving its own. The keypair is real (2048-bit RSA), but ONLY for
 * tests — never deploy it.
 */
public final class JwtTestKeys {

    public static final String PRIVATE_KEY_PEM = readClasspath("/jwt/test-private-key.pem");
    public static final String PUBLIC_KEY_PEM = readClasspath("/jwt/test-public-key.pem");

    private JwtTestKeys() {}

    private static String readClasspath(String path) {
        try (var in = JwtTestKeys.class.getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(in,
                    "test JWT key not found on classpath: " + path).readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test JWT key: " + path, e);
        }
    }
}
