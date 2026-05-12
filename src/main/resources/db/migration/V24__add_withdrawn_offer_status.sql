-- V24: add WITHDRAWN as a distinct offer status.
--
-- Persona audit (Temi) flagged: "no way to withdraw a PENDING offer. If I change
-- my mind, or I made a typo on the amount, I'm stuck waiting for the owner to
-- decline." DECLINED is owner-side semantics. WITHDRAWN is applicant-side.

ALTER TABLE offers DROP CONSTRAINT offers_status_check;
ALTER TABLE offers ADD CONSTRAINT offers_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'COUNTERED', 'WITHDRAWN'));
