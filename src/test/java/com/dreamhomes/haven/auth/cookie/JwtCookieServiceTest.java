package com.dreamhomes.haven.auth.cookie;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCookieServiceTest {

    @Test
    void whenDisabledAddTokenDoesNotWriteHeader() {
        JwtCookieService svc = new JwtCookieService(false, "haven_access", true, "/", "", "Lax", 3_600_000L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        svc.addTokenCookie(response, "secret-jwt");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void whenDisabledReadTokenReturnsEmpty() {
        JwtCookieService svc = new JwtCookieService(false, "haven_access", true, "/", "", "Lax", 3_600_000L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("haven_access", "x"));

        assertThat(svc.readToken(request)).isEmpty();
    }

    @Test
    void whenEnabledReadTokenReturnsMatchingCookie() {
        JwtCookieService svc = new JwtCookieService(true, "haven_access", false, "/", "", "Lax", 3_600_000L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("haven_access", "the-token"));

        assertThat(svc.readToken(request)).contains("the-token");
    }

    @Test
    void whenEnabledAddTokenSetsHttpOnlyCookieWithSameSite() {
        JwtCookieService svc = new JwtCookieService(true, "haven_access", false, "/", "", "Strict", 3_600_000L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        svc.addTokenCookie(response, "abc");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("haven_access=abc");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void whenDomainConfiguredSetCookieIncludesDomainAttribute() {
        JwtCookieService svc = new JwtCookieService(true, "haven_access", true, "/", ".dreamhomes.test", "Lax", 3_600_000L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        svc.addTokenCookie(response, "tok");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).containsIgnoringCase("Domain=.dreamhomes.test");
    }

    @Test
    void clearCookieWhenEnabledWritesZeroMaxAge() {
        JwtCookieService svc = new JwtCookieService(true, "haven_access", true, "/", "", "Lax", 3_600_000L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        svc.clearTokenCookie(response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("haven_access=");
        assertThat(setCookie).containsIgnoringCase("Max-Age=0");
    }

    @Test
    void blankJwtDoesNotSetCookie() {
        JwtCookieService svc = new JwtCookieService(true, "haven_access", true, "/", "", "Lax", 3_600_000L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        svc.addTokenCookie(response, "   ");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
