package com.dreamhomes.haven.listingreport.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * One user can only report a single listing once. The DB unique constraint
 * {@code listing_reports_one_per_user_per_listing} is the source of truth; this
 * exception surfaces it as 409.
 */
public class DuplicateListingReportException extends DomainException {

    public DuplicateListingReportException(Long listingId) {
        super(HttpStatus.CONFLICT, "You have already reported listing " + listingId);
    }
}
