package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.service.UserCredentialsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.auth.service.JwtService;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header on every request, validates the
 * token via {@link JwtService}, and populates the {@link SecurityContextHolder} with a
 * pre-authenticated principal carrying the user's role authority ({@code ROLE_<ROLE>}).
 *
 * <p>If the header is missing, malformed, or the token is invalid, the filter leaves the
 * security context empty and lets the request proceed — downstream rules
 * ({@code anyRequest().authenticated()}) decide whether the request is rejected.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserCredentialsService userCredentialsService;
    private final com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                JwtPrincipal principal = jwtService.parse(token);
                java.util.UUID jti = jwtService.parseJti(token);
                if (jti != null && jwtBlocklistRepository.existsByJti(jti)) {
                    log.warn("Rejecting bearer token for userId={} — jti {} on blocklist (device-scoped logout)",
                            principal.userId(), jti);
                } else if (tokenVersionMatchesCurrent(principal)) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    log.warn("Rejecting bearer token for userId={} — tokenVersion mismatch (revoked)", principal.userId());
                }
            } catch (RuntimeException badToken) {
                // Catches JwtException (signature/expiry/format) AND any other RuntimeException
                // bubbling out of parse — e.g. Role.valueOf throwing IllegalArgumentException
                // when a token carries a now-unknown role. Either way: skip auth, let the
                // downstream rules return 401 (or 200 for permitAll endpoints). Never 500.
                log.warn("Rejecting bearer token: {}", badToken.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * One DB roundtrip per authenticated request, hidden behind {@link UserCredentialsService}.
     * Acceptable for our scale; cache with a short TTL (or fold the version into the JWT
     * with a refresh policy) if/when this shows up in profiles.
     */
    private boolean tokenVersionMatchesCurrent(JwtPrincipal principal) {
        OptionalInt current = userCredentialsService.tokenVersionOf(principal.userId());
        return current.isPresent() && current.getAsInt() == principal.tokenVersion();
    }
}
