package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListingVideoController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class ListingVideoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ListingVideoService listingVideoService;

    @MockBean
    JwtService jwtService;

    @MockBean
    com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    UserCredentialsService userCredentialsService;

    @Test
    void ownerPostsVideoReturns201WithPhotoShapedPayload() throws Exception {
        Instant uploaded = Instant.parse("2026-05-10T12:00:00Z");
        when(listingVideoService.add(eq(50L), eq(7L), eq("https://youtu.be/abc"), eq("Tour")))
                .thenReturn(ListingVideo.builder()
                        .id(99L).listingId(7L).url("https://youtu.be/abc").displayOrder(1)
                        .caption("Tour").uploadedAt(uploaded).build());

        mockMvc.perform(post("/api/listings/7/videos")
                        .with(asOwner(50L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://youtu.be/abc", "caption": "Tour" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(99)))
                .andExpect(jsonPath("$.listingId", is(7)))
                .andExpect(jsonPath("$.url", is("https://youtu.be/abc")))
                .andExpect(jsonPath("$.displayOrder", is(1)))
                .andExpect(jsonPath("$.caption", is("Tour")));

        verify(listingVideoService).add(50L, 7L, "https://youtu.be/abc", "Tour");
    }

    @Test
    void applicantCannotPostVideo() throws Exception {
        mockMvc.perform(post("/api/listings/7/videos")
                        .with(asApplicant(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"url\": \"https://youtu.be/x\" }"))
                .andExpect(status().isForbidden());

        verify(listingVideoService, never()).add(anyLong(), anyLong(), any(), any());
    }

    @Test
    void publicListVideosDoesNotRequireAuth() throws Exception {
        Instant t = Instant.parse("2026-05-10T09:00:00Z");
        when(listingVideoService.list(7L)).thenReturn(List.of(
                ListingVideo.builder().id(1L).listingId(7L).url("https://a.test/v")
                        .displayOrder(1).caption(null).uploadedAt(t).build()));

        mockMvc.perform(get("/api/listings/7/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].url", is("https://a.test/v")));
    }

    @Test
    void ownerDeletesVideoReturns204() throws Exception {
        mockMvc.perform(delete("/api/listings/videos/88")
                        .with(asOwner(50L)))
                .andExpect(status().isNoContent());

        verify(listingVideoService).delete(50L, 88L);
    }

    private static RequestPostProcessor asOwner(long userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "o@example.com", Role.OWNER, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        return authentication(auth);
    }

    private static RequestPostProcessor asApplicant(long userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "a@example.com", Role.APPLICANT, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return authentication(auth);
    }
}
