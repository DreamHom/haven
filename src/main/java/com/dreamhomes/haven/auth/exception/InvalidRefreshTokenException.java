package com.dreamhomes.haven.auth.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown by {@code RefreshTokenService.rotate} when the presented token is unknown,
 * expired, revoked, or its associated user is suspended. Always 401 — same response
 * shape regardless of which of those happened, so the API can't be used to probe
 * which tokens were ever valid.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
    }
}
