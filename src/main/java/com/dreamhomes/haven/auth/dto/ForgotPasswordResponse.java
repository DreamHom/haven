package com.dreamhomes.haven.auth.dto;

/**
 * Anti-enumeration: {@code accepted} is always true on the wire. {@code debugResetToken}
 * is populated only when {@code haven.auth.debug-return-reset-token} is true (integration
 * tests and local debugging); the same raw token is then also logged at WARN on the server.
 * Never enable in production.
 */
public record ForgotPasswordResponse(boolean accepted, String debugResetToken) {
}
