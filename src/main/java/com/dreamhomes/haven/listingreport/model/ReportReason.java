package com.dreamhomes.haven.listingreport.model;

/**
 * The fixed set of reasons a user may pick from when reporting a listing. Stored as
 * its enum name in {@code listing_reports.reason}. Adding a value is a code-only
 * change; removing or renaming requires a migration.
 */
public enum ReportReason {
    /** Looks like a scam — unrealistic price, off-platform demands, missing details. */
    SCAM,
    /** Owner asked for fees outside the platform (deposit, "viewing fee", etc.). */
    OFF_PLATFORM_FEES,
    /** Listing is no longer active but still LIVE — already rented/sold elsewhere. */
    STALE_OR_TAKEN,
    /** Photos / description are inappropriate, unsafe, or violate community rules. */
    INAPPROPRIATE_CONTENT,
    /** None of the above — reporter elaborates in {@code details}. */
    OTHER
}
