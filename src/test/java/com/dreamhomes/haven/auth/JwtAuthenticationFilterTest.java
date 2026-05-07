package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtService jwtService;

    @Mock
    UserRepository userRepository;

    @Mock
    FilterChain chain;

    JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static User userWithTokenVersion(long id, int tv) {
        return User.builder()
                .id(id)
                .email("ada@example.com")
                .role(Role.OWNER)
                .fullName("Ada")
                .tokenVersion(tv)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void setsAuthenticationWhenBearerTokenIsValidAndTokenVersionMatches() throws Exception {
        when(jwtService.parse("valid-token"))
                .thenReturn(new JwtPrincipal(7L, "ada@example.com", Role.OWNER, 3));
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithTokenVersion(7L, 3)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isInstanceOf(JwtPrincipal.class);
        assertThat(((JwtPrincipal) auth.getPrincipal()).email()).isEqualTo("ada@example.com");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_OWNER");

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void rejectsTokenWithStaleTokenVersion() throws Exception {
        // Token claims tv=3, but current user is tv=4 (e.g. a logout happened after issuance).
        when(jwtService.parse("stale-token"))
                .thenReturn(new JwtPrincipal(7L, "ada@example.com", Role.OWNER, 3));
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithTokenVersion(7L, 4)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void rejectsTokenWhenUserNoLongerExists() throws Exception {
        when(jwtService.parse("orphan-token"))
                .thenReturn(new JwtPrincipal(99L, "ghost@example.com", Role.OWNER, 1));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer orphan-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void leavesContextEmptyWhenNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void leavesContextEmptyWhenAuthorizationHeaderHasNoBearerPrefix() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic some-creds");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void leavesContextEmptyAndContinuesChainWhenTokenIsInvalid() throws Exception {
        when(jwtService.parse("rotten-token")).thenThrow(new JwtException("bad token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer rotten-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void handlesUnexpectedRuntimeExceptionsFromJwtServiceAsBadToken() throws Exception {
        // e.g. Role.valueOf throwing IllegalArgumentException when a token carries a role
        // string that no longer exists in the enum after a rename. Should be 401-equivalent
        // (silent skip), not a 500.
        when(jwtService.parse("alien-token")).thenThrow(new IllegalArgumentException("No enum constant"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer alien-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }
}
