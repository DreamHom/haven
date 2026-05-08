package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock AdminAuditLogRepository auditLogRepository;

    AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, auditLogRepository, new ObjectMapper(),
                new AdminMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void suspendingUserStampsSuspendedAtAndBumpsTokenVersionToInvalidateOutstandingTokens() {
        User user = activeUser(50L, Role.OWNER);
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        service.suspend(7L, 50L, "Repeated policy violations");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getSuspendedAt()).isNotNull();
        // Outstanding JWTs are invalidated by the auth filter on the next request.
        assertThat(cap.getValue().getTokenVersion()).isEqualTo(2);
    }

    @Test
    void suspendingWritesAuditLogWithReason() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(activeUser(50L, Role.AGENT)));

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
    void cannotSuspendAlreadySuspendedUser() {
        User user = activeUser(50L, Role.OWNER);
        user.setSuspendedAt(Instant.now());
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.suspend(7L, 50L, "any"))
                .isInstanceOf(UserAlreadySuspendedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void cannotSuspendSelfToPreventLockout() {
        assertThatThrownBy(() -> service.suspend(7L, 7L, "x"))
                .isInstanceOf(CannotModerateSelfException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void suspendRequiresReason() {
        assertThatThrownBy(() -> service.suspend(7L, 50L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void suspendingNonExistentUserThrows404() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(7L, 404L, "x"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void reactivatingClearsSuspendedAtAndAuditLogs() {
        User user = activeUser(50L, Role.OWNER);
        user.setSuspendedAt(Instant.now());
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));

        service.reactivate(7L, 50L);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getSuspendedAt()).isNull();

        ArgumentCaptor<AdminAuditLog> auditCap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().getAction()).isEqualTo(AdminAction.USER_REACTIVATED);
    }

    @Test
    void cannotReactivateUserThatIsNotSuspended() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(activeUser(50L, Role.OWNER)));

        assertThatThrownBy(() -> service.reactivate(7L, 50L))
                .isInstanceOf(UserNotSuspendedException.class);

        verify(userRepository, never()).save(any());
    }

    private static User activeUser(Long id, Role role) {
        return User.builder().id(id).email("u@x").passwordHash("x").fullName("U")
                .role(role).tokenVersion(1).createdAt(Instant.now()).build();
    }
}
