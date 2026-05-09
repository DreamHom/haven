CREATE INDEX IF NOT EXISTS idx_properties_owner_id ON properties (owner_id);
CREATE INDEX IF NOT EXISTS idx_listings_property_id ON listings (property_id);
CREATE INDEX IF NOT EXISTS idx_agent_listings_agent_id ON agent_listings (agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_listings_listing_id ON agent_listings (listing_id);
CREATE INDEX IF NOT EXISTS idx_offers_listing_id ON offers (listing_id);
CREATE INDEX IF NOT EXISTS idx_offers_applicant_id ON offers (applicant_id);
CREATE INDEX IF NOT EXISTS idx_inspection_slots_listing_id ON inspection_slots (listing_id);
CREATE INDEX IF NOT EXISTS idx_inspection_slots_agent_id ON inspection_slots (agent_id);
CREATE INDEX IF NOT EXISTS idx_inspection_requests_slot_id ON inspection_requests (slot_id);
CREATE INDEX IF NOT EXISTS idx_inspection_requests_applicant_id ON inspection_requests (applicant_id);
CREATE INDEX IF NOT EXISTS idx_comments_listing_id ON comments (listing_id);
CREATE INDEX IF NOT EXISTS idx_comments_user_id ON comments (user_id);
CREATE INDEX IF NOT EXISTS idx_verifications_subject_user_id ON verifications (subject_user_id);
CREATE INDEX IF NOT EXISTS idx_verifications_property_id ON verifications (property_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications (user_id);

ALTER TABLE properties
    ADD CONSTRAINT fk_properties_owner
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE listings
    ADD CONSTRAINT fk_listings_property
    FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT;

ALTER TABLE agent_listings
    ADD CONSTRAINT fk_agent_listings_agent
    FOREIGN KEY (agent_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE agent_listings
    ADD CONSTRAINT fk_agent_listings_listing
    FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT;

ALTER TABLE agent_listings
    ADD CONSTRAINT uk_agent_listings_agent_listing
    UNIQUE (agent_id, listing_id);

ALTER TABLE offers
    ADD CONSTRAINT fk_offers_listing
    FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT;

ALTER TABLE offers
    ADD CONSTRAINT fk_offers_applicant
    FOREIGN KEY (applicant_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE inspection_slots
    ADD CONSTRAINT fk_inspection_slots_listing
    FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT;

ALTER TABLE inspection_slots
    ADD CONSTRAINT fk_inspection_slots_agent
    FOREIGN KEY (agent_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE inspection_requests
    ADD CONSTRAINT fk_inspection_requests_slot
    FOREIGN KEY (slot_id) REFERENCES inspection_slots (id) ON DELETE RESTRICT;

ALTER TABLE inspection_requests
    ADD CONSTRAINT fk_inspection_requests_applicant
    FOREIGN KEY (applicant_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_listing
    FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT;

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE verifications
    ADD CONSTRAINT fk_verifications_subject_user
    FOREIGN KEY (subject_user_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE verifications
    ADD CONSTRAINT fk_verifications_property
    FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT;