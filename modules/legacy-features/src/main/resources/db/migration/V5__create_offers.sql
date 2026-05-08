-- Offers: an applicant's formal bid on a listing. owner_id is denormalised from
-- listings.owner_id so authorisation queries don't need a join. status is terminal
-- once it leaves PENDING — accepted or declined offers stay that way.
CREATE TABLE offers (
    id            BIGSERIAL      PRIMARY KEY,
    listing_id    BIGINT         NOT NULL REFERENCES listings(id),
    applicant_id  BIGINT         NOT NULL REFERENCES users(id),
    owner_id      BIGINT         NOT NULL REFERENCES users(id),
    amount        NUMERIC(14, 2) NOT NULL,
    currency      VARCHAR(3)     NOT NULL DEFAULT 'NGN',
    message       TEXT,
    status        VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT offers_status_check    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT offers_amount_positive CHECK (amount > 0)
);
