-- Vista: owner lead / contact reveal + agent marketing gallery.

CREATE TABLE listing_leads (
    id                  BIGSERIAL PRIMARY KEY,
    listing_id          BIGINT       NOT NULL REFERENCES listings(id),
    applicant_user_id   BIGINT       NOT NULL REFERENCES users(id),
    message             TEXT,
    contact_phone       VARCHAR(64)  NOT NULL,
    contact_email       VARCHAR(255) NOT NULL,
    revealed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX listing_leads_listing_created_idx ON listing_leads(listing_id, created_at DESC);

CREATE TABLE agent_marketing_media (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id),
    url            VARCHAR(2048) NOT NULL,
    caption        VARCHAR(512),
    display_order  INT          NOT NULL DEFAULT 0,
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX agent_marketing_media_user_idx ON agent_marketing_media(user_id, display_order, id);
