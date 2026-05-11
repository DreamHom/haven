package com.dreamhomes.haven.admin.dto;

/**
 * One-shot snapshot of platform-health counts for the admin dashboard.
 *
 * <p>Each field maps to a single {@code count(*) WHERE ...} query against the
 * relevant repository. Numbers are computed at request time — no caching, no
 * background pre-aggregation. The endpoint is admin-only and called sparingly,
 * so the per-request DB cost is fine. If usage grows (e.g. polled by a
 * dashboard widget), revisit and back this with materialised view or a
 * Micrometer metric pipeline.</p>
 */
public record AnalyticsSummaryResponse(
        /** All registered users, regardless of role or suspension state. */
        long totalUsers,
        /** Users whose {@code suspended_at} timestamp is non-null. */
        long suspendedUsers,
        /** Listings currently visible on the public browse surface. */
        long openListings,
        /** Listings deal-completed (status `CLOSED`). */
        long closedListings,
        /** Verifications waiting for an admin decision (Dayo's work queue depth). */
        long pendingVerifications,
        /** Offers currently in {@code PENDING} — open deals where someone owes a response. */
        long pendingOffers
) {
}
