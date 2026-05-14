-- V32: listing marketing / discovery fields (Vista §8 — virtual tour URL, negotiable flag).

ALTER TABLE listings
    ADD COLUMN virtual_tour_url VARCHAR(2048),
    ADD COLUMN price_negotiable BOOLEAN NOT NULL DEFAULT false;
