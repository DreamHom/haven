-- Property: the physical thing (address, type, room counts, size, description).
-- Owner_id FK uses NO ACTION (Postgres default) — deleting a user with properties
-- errors loudly. Wholesale user wipe needs a deliberate cleanup pass first.
CREATE TABLE properties (
    id          BIGSERIAL    PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES users(id),
    type        VARCHAR(32)  NOT NULL,
    address     VARCHAR(500) NOT NULL,
    bedrooms    INT,
    bathrooms   INT,
    size_sqm    NUMERIC(10, 2),
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT properties_type_check
        CHECK (type IN ('APARTMENT', 'HOUSE', 'LAND', 'COMMERCIAL'))
);

-- Listing: the market expression of a property. One property can have multiple
-- listings over time (re-listings, simultaneous rent + sale). owner_id is
-- denormalised from properties.owner_id so ownership-check queries don't
-- need a join — the service guarantees the two stay in sync at write time.
CREATE TABLE listings (
    id              BIGSERIAL      PRIMARY KEY,
    property_id     BIGINT         NOT NULL REFERENCES properties(id),
    owner_id        BIGINT         NOT NULL REFERENCES users(id),
    listing_type    VARCHAR(32)    NOT NULL,
    asking_price    NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'NGN',
    caution_fee     NUMERIC(12, 2),
    service_charge  NUMERIC(12, 2),
    agency_fee      NUMERIC(12, 2),
    status          VARCHAR(32)    NOT NULL DEFAULT 'LIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT listings_type_check    CHECK (listing_type IN ('RENT', 'SALE')),
    CONSTRAINT listings_status_check  CHECK (status IN ('LIVE', 'PAUSED', 'CLOSED')),
    CONSTRAINT listings_price_positive CHECK (asking_price > 0)
);
