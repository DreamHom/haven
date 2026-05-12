package com.dreamhomes.haven.listingreport.model;

/**
 * Moderation lifecycle of a user-filed listing report.
 * <ul>
 *   <li>{@link #PENDING} — newly filed, waiting for an admin to triage.</li>
 *   <li>{@link #RESOLVED} — admin acted on it (took the listing down, suspended
 *       the owner, etc.) and recorded the resolution note.</li>
 *   <li>{@link #DISMISSED} — admin reviewed and judged not actionable. Note
 *       still captured so the queue isn't silently flushed.</li>
 * </ul>
 */
public enum ListingReportStatus {
    PENDING,
    RESOLVED,
    DISMISSED
}
