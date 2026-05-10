package com.dreamhomes.haven.agentlisting;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owner-to-agent assignment handshake (PRD §4.1, userflows §3).
 *
 * <p>Three transitions live here. Each one writes a sync notification (PRD §7 keeps
 * Kafka to the two big events only):
 * <ul>
 *   <li>{@link #request(Long, Long, Long)} — owner invites agent → agent notified.</li>
 *   <li>{@link #respond(Long, Long, AgentListingStatus, String)} — agent accepts /
 *       declines → owner notified.</li>
 *   <li>{@link #revoke(Long, Role, Long, String)} — either party ends the relationship
 *       (or the owner withdraws a still-pending invite) → the OTHER party notified.</li>
 * </ul>
 *
 * <p>The verified-agent badge (`agent_profiles.credential_verified_at`) is intentionally
 * NOT a gate here — owners discover and filter on it via the public profile endpoint;
 * this service only enforces {@code role = AGENT}. Matches the broader theme that
 * verification is non-blocking but rewarded (PRD §4.1).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentListingService {

    private final AgentListingRepository agentListingRepository;
    private final ListingService listingService;
    private final UserProfileService userProfileService;
    private final NotificationApi notificationApi;

    @Transactional
    public AgentListing request(Long ownerId, Long listingId, Long agentId) {
        Long listingOwner = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (!listingOwner.equals(ownerId)) {
            throw new NotPropertyOwnerException();
        }
        // Target must exist AND be an agent — single 404 either way; don't leak existence.
        Role role = userProfileService.roleOf(agentId)
                .orElseThrow(() -> new AgentNotFoundOrWrongRoleException(agentId));
        if (role != Role.AGENT) {
            throw new AgentNotFoundOrWrongRoleException(agentId);
        }

        if (agentListingRepository.existsByListingIdAndStatus(listingId, AgentListingStatus.REQUESTED)) {
            throw new ListingAlreadyHasPendingInviteException(listingId);
        }
        if (agentListingRepository.existsByListingIdAndStatus(listingId, AgentListingStatus.ACCEPTED)) {
            throw new ListingAlreadyHasActiveAgentException(listingId);
        }

        Instant now = Instant.now();
        AgentListing saved = agentListingRepository.save(AgentListing.builder()
                .listingId(listingId)
                .agentUserId(agentId)
                .requestedByOwnerId(ownerId)
                .status(AgentListingStatus.REQUESTED)
                .requestedAt(now)
                .build());

        notify(agentId, NotificationKind.AGENT_ASSIGNMENT_REQUESTED, saved, null);

        log.info("Owner {} requested assignment of listing {} to agent {} → assignmentId={}",
                ownerId, listingId, agentId, saved.getId());
        return saved;
    }

    @Transactional
    public AgentListing respond(Long callerAgentId, Long assignmentId,
                                AgentListingStatus newStatus, String reason) {
        if (newStatus != AgentListingStatus.ACCEPTED && newStatus != AgentListingStatus.DECLINED) {
            throw new IllegalArgumentException("respond() only accepts ACCEPTED or DECLINED, got " + newStatus);
        }
        if (newStatus == AgentListingStatus.DECLINED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Decline reason is required");
        }
        AgentListing assignment = agentListingRepository.findById(assignmentId)
                .orElseThrow(() -> new AgentListingNotFoundException(assignmentId));
        if (!assignment.getAgentUserId().equals(callerAgentId)) {
            throw new NotTargetedAgentException();
        }
        if (assignment.getStatus() != AgentListingStatus.REQUESTED) {
            throw new AgentListingAlreadyDecidedException(assignmentId, assignment.getStatus());
        }

        Instant now = Instant.now();
        assignment.setStatus(newStatus);
        assignment.setDecidedAt(now);
        assignment.setDecisionReason(reason);
        agentListingRepository.save(assignment);

        NotificationKind kind = newStatus == AgentListingStatus.ACCEPTED
                ? NotificationKind.AGENT_ASSIGNMENT_ACCEPTED
                : NotificationKind.AGENT_ASSIGNMENT_DECLINED;
        notify(assignment.getRequestedByOwnerId(), kind, assignment, reason);

        log.info("Agent {} responded to assignmentId={} with status={}",
                callerAgentId, assignmentId, newStatus);
        return assignment;
    }

    /**
     * Either party can revoke — owners switch agents, agents resign. Admins can also
     * revoke as part of platform moderation (kept here rather than mirrored in
     * AdminListingService for simplicity; an admin-action audit row could be added
     * by a future moderation service if it needs separate accounting).
     */
    @Transactional
    public AgentListing revoke(Long callerId, Role callerRole, Long assignmentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Revoke reason is required");
        }
        AgentListing assignment = agentListingRepository.findById(assignmentId)
                .orElseThrow(() -> new AgentListingNotFoundException(assignmentId));
        if (assignment.getStatus() != AgentListingStatus.REQUESTED
                && assignment.getStatus() != AgentListingStatus.ACCEPTED) {
            throw new AgentListingNotActiveException(assignmentId, assignment.getStatus());
        }
        if (!isAuthorisedToRevoke(callerId, callerRole, assignment)) {
            throw new NotAuthorisedToRevokeException();
        }

        Instant now = Instant.now();
        assignment.setStatus(AgentListingStatus.REVOKED);
        assignment.setDecidedAt(now);
        assignment.setDecisionReason(reason);
        agentListingRepository.save(assignment);

        Long otherParty = callerId.equals(assignment.getRequestedByOwnerId())
                ? assignment.getAgentUserId()
                : assignment.getRequestedByOwnerId();
        notify(otherParty, NotificationKind.AGENT_ASSIGNMENT_REVOKED, assignment, reason);

        log.info("Caller {} ({}) revoked assignmentId={} reason='{}'",
                callerId, callerRole, assignmentId, reason);
        return assignment;
    }

    @Transactional(readOnly = true)
    public Page<AgentListing> listMine(Long callerId, Role callerRole, Pageable pageable) {
        return switch (callerRole) {
            case AGENT -> agentListingRepository.findByAgentUserIdOrderByRequestedAtDesc(callerId, pageable);
            case OWNER -> agentListingRepository.findByRequestedByOwnerIdOrderByRequestedAtDesc(callerId, pageable);
            default -> Page.empty(pageable);
        };
    }

    private boolean isAuthorisedToRevoke(Long callerId, Role callerRole, AgentListing assignment) {
        if (callerRole == Role.ADMIN) {
            return true;
        }
        return callerId.equals(assignment.getRequestedByOwnerId())
                || callerId.equals(assignment.getAgentUserId());
    }

    private void notify(Long recipientId, NotificationKind kind, AgentListing assignment, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignment.getId());
        payload.put("listingId", assignment.getListingId());
        payload.put("agentUserId", assignment.getAgentUserId());
        payload.put("ownerUserId", assignment.getRequestedByOwnerId());
        payload.put("status", assignment.getStatus().name());
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        notificationApi.recordSync(kind, recipientId, payload);
    }
}
