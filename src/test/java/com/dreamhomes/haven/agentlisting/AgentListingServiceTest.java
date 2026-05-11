package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.agentlisting.exception.AgentListingAlreadyDecidedException;
import com.dreamhomes.haven.agentlisting.exception.AgentListingNotActiveException;
import com.dreamhomes.haven.agentlisting.exception.AgentNotFoundOrWrongRoleException;
import com.dreamhomes.haven.agentlisting.exception.ListingAlreadyHasActiveAgentException;
import com.dreamhomes.haven.agentlisting.exception.ListingAlreadyHasPendingInviteException;
import com.dreamhomes.haven.agentlisting.exception.NotAuthorisedToRevokeException;
import com.dreamhomes.haven.agentlisting.exception.NotTargetedAgentException;
import com.dreamhomes.haven.agentlisting.model.AgentListing;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;

@ExtendWith(MockitoExtension.class)
class AgentListingServiceTest {

    @Mock AgentListingRepository agentListingRepository;
    @Mock ListingService listingService;
    @Mock UserProfileService userProfileService;
    @Mock NotificationApi notificationApi;

    AgentListingService service;

    @BeforeEach
    void setUp() {
        service = new AgentListingService(agentListingRepository, listingService, userProfileService, notificationApi);
    }

    // ============================ request ============================

    @Test
    void ownerInvitingAgentPersistsRequestedRowAndNotifiesAgent() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));
        when(userProfileService.roleOf(60L)).thenReturn(Optional.of(Role.AGENT));
        when(agentListingRepository.save(any(AgentListing.class))).thenAnswer(inv -> {
            AgentListing al = inv.getArgument(0);
            al.setId(123L);
            return al;
        });

        AgentListing requested = service.request(/*ownerId=*/50L, 7L, 60L);

        ArgumentCaptor<AgentListing> alCap = ArgumentCaptor.forClass(AgentListing.class);
        verify(agentListingRepository).save(alCap.capture());
        AgentListing saved = alCap.getValue();
        assertThat(saved.getListingId()).isEqualTo(7L);
        assertThat(saved.getAgentUserId()).isEqualTo(60L);
        assertThat(saved.getRequestedByOwnerId()).isEqualTo(50L);
        assertThat(saved.getStatus()).isEqualTo(AgentListingStatus.REQUESTED);
        assertThat(saved.getDecidedAt()).isNull();
        assertThat(saved.getRequestedAt()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(notificationApi).recordSync(eq(NotificationKind.AGENT_ASSIGNMENT_REQUESTED), eq(60L),
                payloadCap.capture());
        assertThat(payloadCap.getValue()).containsEntry("assignmentId", 123L).containsEntry("listingId", 7L);
        assertThat(requested.getId()).isEqualTo(123L);
    }

    @Test
    void requestRejectsNonOwnerOfListing() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(99L));

        assertThatThrownBy(() -> service.request(/*callerId=*/50L, 7L, 60L))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(agentListingRepository, never()).save(any());
    }

    @Test
    void requestRejectsTargetingNonAgentUser() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));
        when(userProfileService.roleOf(60L)).thenReturn(Optional.of(Role.APPLICANT));

        assertThatThrownBy(() -> service.request(50L, 7L, 60L))
                .isInstanceOf(AgentNotFoundOrWrongRoleException.class);
    }

    @Test
    void requestRejectsWhenListingAlreadyHasPendingInvite() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));
        when(userProfileService.roleOf(60L)).thenReturn(Optional.of(Role.AGENT));
        when(agentListingRepository.existsByListingIdAndStatus(7L, AgentListingStatus.REQUESTED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.request(50L, 7L, 60L))
                .isInstanceOf(ListingAlreadyHasPendingInviteException.class);

        verify(agentListingRepository, never()).save(any());
    }

    @Test
    void requestRejectsWhenListingAlreadyHasActiveAgent() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));
        when(userProfileService.roleOf(60L)).thenReturn(Optional.of(Role.AGENT));
        when(agentListingRepository.existsByListingIdAndStatus(7L, AgentListingStatus.REQUESTED))
                .thenReturn(false);
        when(agentListingRepository.existsByListingIdAndStatus(7L, AgentListingStatus.ACCEPTED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.request(50L, 7L, 60L))
                .isInstanceOf(ListingAlreadyHasActiveAgentException.class);
    }

    @Test
    void requestThrowsWhenListingNotFound() {
        when(listingService.ownerOf(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(50L, 404L, 60L))
                .isInstanceOf(ListingNotFoundException.class);
    }

    // ============================ respond (accept / decline) ============================

    @Test
    void targetedAgentAcceptingFlipsStatusAndNotifiesOwner() {
        AgentListing pending = pending(123L, /*listingId=*/7L, /*ownerId=*/50L, /*agentId=*/60L);
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(pending));

        service.respond(/*callerAgentId=*/60L, 123L, AgentListingStatus.ACCEPTED, null);

        ArgumentCaptor<AgentListing> cap = ArgumentCaptor.forClass(AgentListing.class);
        verify(agentListingRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AgentListingStatus.ACCEPTED);
        assertThat(cap.getValue().getDecidedAt()).isNotNull();

        verify(notificationApi).recordSync(eq(NotificationKind.AGENT_ASSIGNMENT_ACCEPTED), eq(50L), any());
    }

    @Test
    void targetedAgentDecliningStoresReasonAndNotifiesOwner() {
        AgentListing pending = pending(123L, 7L, 50L, 60L);
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(pending));

        service.respond(60L, 123L, AgentListingStatus.DECLINED, "Booked solid this quarter");

        ArgumentCaptor<AgentListing> cap = ArgumentCaptor.forClass(AgentListing.class);
        verify(agentListingRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AgentListingStatus.DECLINED);
        assertThat(cap.getValue().getDecisionReason()).isEqualTo("Booked solid this quarter");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(notificationApi).recordSync(eq(NotificationKind.AGENT_ASSIGNMENT_DECLINED), eq(50L),
                payloadCap.capture());
        assertThat(payloadCap.getValue()).containsEntry("reason", "Booked solid this quarter");
    }

    @Test
    void respondingAgentMustBeTheTargetedAgent() {
        AgentListing pending = pending(123L, 7L, 50L, /*agentId=*/60L);
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.respond(/*callerId=*/61L, 123L, AgentListingStatus.ACCEPTED, null))
                .isInstanceOf(NotTargetedAgentException.class);

        verify(agentListingRepository, never()).save(any());
    }

    @Test
    void respondRejectsNonRequestedRows() {
        AgentListing accepted = pending(123L, 7L, 50L, 60L);
        accepted.setStatus(AgentListingStatus.ACCEPTED);
        accepted.setDecidedAt(Instant.now());
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service.respond(60L, 123L, AgentListingStatus.ACCEPTED, null))
                .isInstanceOf(AgentListingAlreadyDecidedException.class);
    }

    @Test
    void respondRequiresAcceptedOrDeclinedStatus() {
        // Status check fires before any repo lookup — no findById stub needed.
        assertThatThrownBy(() -> service.respond(60L, 123L, AgentListingStatus.REVOKED, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(agentListingRepository, never()).findById(any());
    }

    @Test
    void declineRequiresReason() {
        // Reason check fires before any repo lookup.
        assertThatThrownBy(() -> service.respond(60L, 123L, AgentListingStatus.DECLINED, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(agentListingRepository, never()).findById(any());
    }

    // ============================ revoke ============================

    @Test
    void ownerCanRevokeAcceptedAssignmentNotifiesAgent() {
        AgentListing active = pending(123L, 7L, /*ownerId=*/50L, /*agentId=*/60L);
        active.setStatus(AgentListingStatus.ACCEPTED);
        active.setDecidedAt(Instant.now().minusSeconds(3600));
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(active));

        service.revoke(/*callerId=*/50L, Role.OWNER, 123L, "Switching agents");

        ArgumentCaptor<AgentListing> cap = ArgumentCaptor.forClass(AgentListing.class);
        verify(agentListingRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AgentListingStatus.REVOKED);
        assertThat(cap.getValue().getDecisionReason()).isEqualTo("Switching agents");

        verify(notificationApi).recordSync(eq(NotificationKind.AGENT_ASSIGNMENT_REVOKED), eq(60L), any());
    }

    @Test
    void agentCanRevokeAcceptedAssignmentNotifiesOwner() {
        AgentListing active = pending(123L, 7L, 50L, /*agentId=*/60L);
        active.setStatus(AgentListingStatus.ACCEPTED);
        active.setDecidedAt(Instant.now().minusSeconds(3600));
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(active));

        service.revoke(/*callerId=*/60L, Role.AGENT, 123L, "Stepping down");

        verify(notificationApi).recordSync(any(), eq(50L), any()); // owner notified
    }

    @Test
    void ownerCanRevokeStillPendingInviteToWithdrawIt() {
        AgentListing pending = pending(123L, 7L, 50L, 60L); // status = REQUESTED
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(pending));

        service.revoke(50L, Role.OWNER, 123L, "Going self-managed");

        ArgumentCaptor<AgentListing> cap = ArgumentCaptor.forClass(AgentListing.class);
        verify(agentListingRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AgentListingStatus.REVOKED);
    }

    @Test
    void revokeRejectsRandomThirdParty() {
        AgentListing active = pending(123L, 7L, 50L, 60L);
        active.setStatus(AgentListingStatus.ACCEPTED);
        active.setDecidedAt(Instant.now());
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.revoke(/*callerId=*/200L, Role.AGENT, 123L, "lol"))
                .isInstanceOf(NotAuthorisedToRevokeException.class);

        verify(agentListingRepository, never()).save(any());
    }

    @Test
    void revokeRejectsTerminalRows() {
        AgentListing declined = pending(123L, 7L, 50L, 60L);
        declined.setStatus(AgentListingStatus.DECLINED);
        declined.setDecidedAt(Instant.now());
        when(agentListingRepository.findById(123L)).thenReturn(Optional.of(declined));

        assertThatThrownBy(() -> service.revoke(50L, Role.OWNER, 123L, "anything"))
                .isInstanceOf(AgentListingNotActiveException.class);
    }

    @Test
    void revokeRequiresReason() {
        assertThatThrownBy(() -> service.revoke(50L, Role.OWNER, 123L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(agentListingRepository, never()).findById(any());
    }

    // ============================ helpers ============================

    private static AgentListing pending(Long id, Long listingId, Long ownerId, Long agentId) {
        return AgentListing.builder()
                .id(id).listingId(listingId).agentUserId(agentId)
                .requestedByOwnerId(ownerId)
                .status(AgentListingStatus.REQUESTED)
                .requestedAt(Instant.now())
                .version(0L)
                .build();
    }
}
