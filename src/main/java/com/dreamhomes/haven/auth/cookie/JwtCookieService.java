package com.dreamhomes.haven.auth.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Optional httpOnly JWT cookie alongside {@code Authorization: Bearer}. Enables
 * same-site browser sessions without localStorage when enabled via configuration.
 */
@Component
public class JwtCookieService {

    private final boolean enabled;
    private final String cookieName;
    private final boolean secure;
    private final String path;
    private final String domain;
    private final String sameSite;
    private final long maxAgeSeconds;

    public JwtCookieService(
            @Value("${haven.auth.jwt-cookie.enabled:false}") boolean enabled,
            @Value("${haven.auth.jwt-cookie.name:haven_access}") String cookieName,
            @Value("${haven.auth.jwt-cookie.secure:true}") boolean secure,
            @Value("${haven.auth.jwt-cookie.path:/}") String path,
            @Value("${haven.auth.jwt-cookie.domain:}") String domain,
            @Value("${haven.auth.jwt-cookie.same-site:Lax}") String sameSite,
            @Value("${haven.jwt.expiration-ms:3600000}") long jwtExpirationMs) {
        this.enabled = enabled;
        this.cookieName = cookieName;
        this.secure = secure;
        this.path = path.isBlank() ? "/" : path;
        this.domain = (domain == null || domain.isBlank()) ? null : domain.trim();
        this.sameSite = (sameSite == null || sameSite.isBlank()) ? "Lax" : sameSite.trim();
        this.maxAgeSeconds = Math.max(1L, jwtExpirationMs / 1000L);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void addTokenCookie(HttpServletResponse response, String jwt) {
        if (!enabled || jwt == null || jwt.isBlank()) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie(jwt, maxAgeSeconds).toString());
    }

    public void clearTokenCookie(HttpServletResponse response) {
        if (!enabled) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie("", 0).toString());
    }

    public Optional<String> readToken(HttpServletRequest request) {
        if (!enabled) {
            return Optional.empty();
        }
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private ResponseCookie tokenCookie(String value, long maxAge) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(maxAge)
                .sameSite(sameSite);
        if (domain != null) {
            b = b.domain(domain);
        }
        return b.build();
    }
}
