package com.dreamhomes.haven.agentlisting.model;

/**
 * Lifecycle of an {@link AgentListing}:
 * <ul>
 *   <li>{@link #REQUESTED} — owner invited the agent; awaiting their response.</li>
 *   <li>{@link #ACCEPTED} — active assignment; the agent now manages the listing.</li>
 *   <li>{@link #DECLINED} — terminal; agent passed. Owner can request a fresh invite to a different agent.</li>
 *   <li>{@link #REVOKED} — terminal; either party ended an active assignment.</li>
 * </ul>
 */
public enum AgentListingStatus {
    REQUESTED,
    ACCEPTED,
    DECLINED,
    REVOKED
}
