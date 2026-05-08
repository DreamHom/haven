package com.dreamhomes.haven.notification;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean NotificationService notificationService;
    @MockBean JwtService jwtService;
    @MockBean UserCredentialsApi userCredentialsApi;

    @Test
    void anonymousCannotReadNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedListMineReturnsPagedInbox() throws Exception {
        Page<Notification> page = new PageImpl<>(
                List.of(stub(1L, 50L, NotificationKind.OFFER_SUBMITTED)),
                PageRequest.of(0, 20), 1);
        when(notificationService.listMine(eq(50L), eq(false), any())).thenReturn(page);

        mockMvc.perform(get("/api/notifications/mine")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].kind", is("OFFER_SUBMITTED")));
    }

    @Test
    void unreadOnlyQueryParamRoutesToFilteredService() throws Exception {
        when(notificationService.listMine(eq(50L), eq(true), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/notifications/mine?unreadOnly=true")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isOk());

        verify(notificationService).listMine(eq(50L), eq(true), any());
    }

    @Test
    void unreadCountReturnsScalarEnvelope() throws Exception {
        when(notificationService.countUnread(50L)).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/mine/unread-count")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread", is(5)));
    }

    @Test
    void markReadDelegatesToService() throws Exception {
        Notification read = stub(123L, 50L, NotificationKind.COMMENT_POSTED);
        read.setReadAt(Instant.now());
        when(notificationService.markRead(50L, 123L)).thenReturn(read);

        mockMvc.perform(post("/api/notifications/123/mark-read")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists());
    }

    @Test
    void anonymousCannotMarkRead() throws Exception {
        mockMvc.perform(post("/api/notifications/123/mark-read"))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).markRead(any(), any());
    }

    private static Notification stub(Long id, Long recipientId, NotificationKind kind) {
        return Notification.builder()
                .id(id).recipientId(recipientId).kind(kind)
                .source(NotificationSource.SYNC).payload("{}")
                .createdAt(Instant.now()).build();
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
