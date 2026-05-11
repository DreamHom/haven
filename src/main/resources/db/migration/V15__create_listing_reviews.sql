-- Phase 10: post-deal reviews. Owner ↔ applicant rate each other after the listing is
-- CLOSED and the applicant had an ACCEPTED offer on it. Agent reviews defer to a future
-- phase tied to AgentListing.
--
-- The data layer guards three invariants. Application-side checks pre-empt them with
-- clean domain exceptions; the constraints are the safety net for races / direct DB
-- writes:
--
--   • rating ∈ [1, 5]
--   • body length 1..2000
--   • UQ (listing_id, reviewer_user_id, reviewee_user_id)
--       → each reviewer reviews each counterparty at most once per listing.
--       Re-buying the same listing later (different deal) would be the same composite
--       key and is genuinely a duplicate review case for capstone scope.
CREATE TABLE listing_reviews (
    id                 BIGSERIAL    PRIMARY KEY,
    listing_id         BIGINT       NOT NULL REFERENCES listings(id),
    reviewer_user_id   BIGINT       NOT NULL REFERENCES users(id),
    reviewee_user_id   BIGINT       NOT NULL REFERENCES users(id),
    rating             SMALLINT     NOT NULL,
    body               TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT listing_reviews_rating_check  CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT listing_reviews_body_len_check CHECK (char_length(body) BETWEEN 1 AND 2000),
    CONSTRAINT listing_reviews_distinct_parties_check CHECK (reviewer_user_id <> reviewee_user_id),
    CONSTRAINT listing_reviews_unique_pair UNIQUE (listing_id, reviewer_user_id, reviewee_user_id)
);

-- Hot read: "all reviews ABOUT this user" — newest first. Backs the public profile
-- aggregate + a future "all reviews" feed.
CREATE INDEX listing_reviews_reviewee_created_idx
    ON listing_reviews (reviewee_user_id, created_at DESC);

-- Hot read: "reviews on this listing" — used internally to gate re-reviews + by an
-- admin moderation feed.
CREATE INDEX listing_reviews_listing_idx
    ON listing_reviews (listing_id);
