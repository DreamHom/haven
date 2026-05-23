-- One interest row per (listing, applicant) — matches service rule; DB enforces under concurrency.
CREATE UNIQUE INDEX listing_leads_listing_applicant_uidx
    ON listing_leads (listing_id, applicant_user_id);
