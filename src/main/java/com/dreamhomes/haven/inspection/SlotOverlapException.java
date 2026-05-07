package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Owner tried to publish a slot that overlaps an existing slot on the same listing.
 * Enforced at the DB by an EXCLUDE USING GIST constraint (PRD §6 — no race conditions
 * at the data layer); the service catches the violation and surfaces it as a 409.
 */
public class SlotOverlapException extends DomainException {

    public SlotOverlapException() {
        super(HttpStatus.CONFLICT, "An overlapping inspection slot already exists for this listing");
    }
}
