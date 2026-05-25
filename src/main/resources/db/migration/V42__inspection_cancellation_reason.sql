-- Gap C of post-session-tasks Item 7: cancellations from PENDING or APPROVED now
-- carry a REQUIRED user-supplied reason, surfaced in the notification to the OTHER
-- party so they understand what happened.
--
-- Column is nullable at the DB layer because the legacy applicant-only cancel path
-- (kept around for backwards compat with the existing DELETE /api/inspections/{id}
-- endpoint) writes null. New callers go through cancelByEitherParty which validates
-- the reason at the service layer (NotBlank, max 200 chars).
ALTER TABLE inspection_requests
    ADD COLUMN cancellation_reason VARCHAR(200);
