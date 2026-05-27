package com.dreamhomes.haven.user.repository;

import java.time.Instant;

/**
 * Spring Data JPA projection for owner-side trust signals embedded on listing payloads.
 *
 * <p>{@code identityVerifiedAt} is non-null when an admin has approved the owner's
 * identity verification (PRD §4.8). Carried alongside {@code publicBio} so the
 * listing service can populate both with a single batch query, no N+1.
 *
 * <p>UI semantics: {@code identityVerifiedAt == null} drives the "⚠️ Possible Scam"
 * warning chip on listing cards / detail; non-null means the owner is verified and
 * the absence-of-warning is the signal (PRD §4.8, Item 16 in post-session-tasks.md).
 */
public interface OwnerTrustRow {

    Long getOwnerId();

    String getPublicBio();

    Instant getIdentityVerifiedAt();
}
