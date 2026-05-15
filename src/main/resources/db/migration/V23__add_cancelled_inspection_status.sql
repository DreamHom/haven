-- V23: add CANCELLED as a distinct inspection-request status.
--
-- Persona audit (Temi) flagged that there's no way to cancel a booked inspection
-- once you've claimed a slot. DECLINED is owner-side semantics ("owner rejected
-- the request"); we need CANCELLED for applicant-side withdrawal so the slot
-- frees up and the owner's notification reads correctly.

ALTER TABLE inspection_requests DROP CONSTRAINT inspection_requests_status_check;
ALTER TABLE inspection_requests ADD CONSTRAINT inspection_requests_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'CANCELLED'));
