package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserCredentialsApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(CommentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class CommentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CommentService commentService;
    @MockBean JwtService jwtService;
    @MockBean UserCredentialsApi userCredentialsApi;

    @Test
    void anonymousVisitorCanListCommentsOnAListing() throws Exception {
        Page<Comment> page = new PageImpl<>(List.of(stub(1L, 7L, 100L, "first")), PageRequest.of(0, 20), 1);
        when(commentService.list(eq(7L), any())).thenReturn(page);

        mockMvc.perform(get("/api/listings/7/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].body", is("first")));
    }

    @Test
    void postingCommentReturns201AndCallsService() throws Exception {
        when(commentService.post(eq(100L), eq(7L), eq("hi"))).thenReturn(stub(50L, 7L, 100L, "hi"));

        mockMvc.perform(post("/api/listings/7/comments")
                        .with(asPrincipal(100L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(50)));
    }

    @Test
    void anonymousCannotPostCommentReturns401() throws Exception {
        mockMvc.perform(post("/api/listings/7/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isUnauthorized());

        verify(commentService, never()).post(any(), any(), any());
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/listings/7/comments")
                        .with(asPrincipal(100L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).post(any(), any(), any());
    }

    @Test
    void deleteCommentReturns204() throws Exception {
        mockMvc.perform(delete("/api/comments/50")
                        .with(asPrincipal(100L, Role.APPLICANT)))
                .andExpect(status().isNoContent());

        verify(commentService).delete(eq(100L), eq(Role.APPLICANT), eq(50L), any());
    }

    @Test
    void deleteCommentWithoutAuthReturns401() throws Exception {
        mockMvc.perform(delete("/api/comments/50"))
                .andExpect(status().isUnauthorized());

        verify(commentService, never()).delete(any(), any(), any(), any());
    }

    private static Comment stub(Long id, Long listingId, Long authorId, String body) {
        return Comment.builder()
                .id(id).listingId(listingId).authorUserId(authorId)
                .body(body).createdAt(Instant.now()).build();
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
