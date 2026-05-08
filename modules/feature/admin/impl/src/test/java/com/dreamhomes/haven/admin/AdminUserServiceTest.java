package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserAdminApi;
import com.dreamhomes.haven.user.UserAdminView;
import com.dreamhomes.haven.user.UserAlreadySuspendedException;
import com.dreamhomes.haven.user.UserNotFoundException;
import com.dreamhomes.haven.user.UserNotSuspendedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminUserService is a thin orchestrator after the UserAdminApi extraction:
 * <ul>
 *   <li>self-moderation guard + reason validation in this layer</li>
 *   <li>delegate the actual user-state mutation to {@link UserAdminApi}</li>
 *   <li>write the admin audit log row + metric</li>
 * </ul>
 * Tests focus on what this layer owns; the suspendedAt/tokenVersion mechanics live
 * in {@code UserAdminServiceTest} inside {@code feature/user/impl}.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserAdminApi userAdminApi;
    @Mock AdminAuditLogRepository auditLogRepository;

    AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userAdminApi, auditLogRepository, new ObjectMapper(),
                new AdminMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void suspendingDelegatesToUserApiAndReturnsView() {
        UserAdminView suspended = view(50L, Role.OWNER, Instant.parse("2026-01-01T00:00:00Z"));
        when(userAdminApi.suspend(50L)).thenReturn(suspended);

        UserAdminView result = service.suspend(7L, 50L, "Repeated policy violations");

        assertThat(result).isSameAs(suspended);
        verify(userAdminApi).suspend(50L);
    }

    @Test
    void suspendingWritesAuditLogWithReason() {
        when(userAdminApi.suspend(50L)).thenReturn(view(50L, Role.AGENT, Instant.now()));

        service.suspend(7L, 50L, "Bad behaviour");

        ArgumentCaptor<AdminAuditLog> cap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getAdminId()).isEqualTo(7L);
        assertThat(cap.getValue().getAction()).isEqualTo(AdminAction.USER_SUSPENDED);
        assertThat(cap.getValue().getTargetType()).isEqualTo(AuditTargetType.USER);
        assertThat(cap.getValue().getTargetId()).isEqualTo(50L);
        assertThat(cap.getValue().getMetadata()).contains("Bad behaviour");
    }

    @Test
    void propagatesUserAlreadySuspendedFromApi() {
        when(userAdminApi.suspend(50L)).thenThrow(new UserAlreadySuspendedException(50L));

        assertThatThrownBy(() -> service.suspend(7L, 50L, "any"))
                .isInstanceOf(UserAlreadySuspendedException.class);

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void cannotSuspendSelfToPreventLockout() {
        assertThatThrownBy(() -> service.suspend(7L, 7L, "x"))
                .isInstanceOf(CannotModerateSelfException.class);

        verify(userAdminApi, never()).suspend(any());
    }

    @Test
    void suspendRequiresReason() {
        assertThatThrownBy(() -> service.suspend(7L, 50L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userAdminApi, never()).suspend(any());
    }

    @Test
    void suspendingNonExistentUserPropagates404FromApi() {
        when(userAdminApi.suspend(404L)).thenThrow(new UserNotFoundException(404L));

        assertThatThrownBy(() -> service.suspend(7L, 404L, "x"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void reactivatingDelegatesAndAuditLogs() {
        UserAdminView reactivated = view(50L, Role.OWNER, null);
        when(userAdminApi.reactivate(50L)).thenReturn(reactivated);

        UserAdminView result = service.reactivate(7L, 50L);

        assertThat(result).isSameAs(reactivated);
        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getAction()).isEqualTo(AdminAction.USER_REACTIVATED);
    }

    @Test
    void propagatesUserNotSuspendedFromApi() {
        when(userAdminApi.reactivate(eq(50L))).thenThrow(new UserNotSuspendedException(50L));

        assertThatThrownBy(() -> service.reactivate(7L, 50L))
                .isInstanceOf(UserNotSuspendedException.class);

        verify(auditLogRepository, never()).save(any());
    }

    private static UserAdminView view(Long id, Role role, Instant suspendedAt) {
        return new UserAdminView(id, "u@x", role, suspendedAt, null);
    }
}
