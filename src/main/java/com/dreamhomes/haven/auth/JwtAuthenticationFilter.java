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


@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserCredentialsService userCredentialsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                JwtPrincipal principal = jwtService.parse(token);
                if (tokenVersionMatchesCurrent(principal)) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } 
                else {
                    log.warn("Rejecting bearer token for userId={} — tokenVersion mismatch (revoked)", principal.userId());
                }

            } 
            catch (RuntimeException badToken) {
          
                log.warn("Rejecting bearer token: {}", badToken.getMessage());
            }
        }
        chain.doFilter(request, response);
    }


    private boolean tokenVersionMatchesCurrent(JwtPrincipal principal) {
        OptionalInt current = userCredentialsService.tokenVersionOf(principal.userId());
        return current.isPresent() && current.getAsInt() == principal.tokenVersion();
    }
}
