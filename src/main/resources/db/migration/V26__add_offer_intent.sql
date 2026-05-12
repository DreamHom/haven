-- V26: add `intent` to offers (RENT / BUY / RENT_TO_BUY).
--
-- Persona audit (Ngozi): "my whole reason for using this platform is rent-to-buy
-- with Moniepoint financing. Cramming that into a free-text message is not a
-- contract — it's a hope." Owners need to route offers differently based on
-- intent; first-class field makes that automatable.
--
-- Default of NULL = "unspecified" (back-compat for offers submitted before this
-- column existed). Frontends can prompt for intent on new submissions.

ALTER TABLE offers
    ADD COLUMN intent VARCHAR(16),
    ADD CONSTRAINT offers_intent_check
        CHECK (intent IS NULL OR intent IN ('RENT', 'BUY', 'RENT_TO_BUY'));
