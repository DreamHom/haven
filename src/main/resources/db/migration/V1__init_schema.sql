CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS properties (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT,
    address_line1 VARCHAR(255),
    city VARCHAR(255),
    state_name VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    status VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS listings (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT,
    type VARCHAR(255),
    status VARCHAR(255),
    price NUMERIC,
    title VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS agent_listings (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT,
    listing_id BIGINT
);

CREATE TABLE IF NOT EXISTS offers (
    id BIGSERIAL PRIMARY KEY,
    listing_id BIGINT,
    applicant_id BIGINT,
    amount NUMERIC,
    status VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS inspection_slots (
    id BIGSERIAL PRIMARY KEY,
    listing_id BIGINT,
    agent_id BIGINT,
    start_at TIMESTAMP WITH TIME ZONE,
    end_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS inspection_requests (
    id BIGSERIAL PRIMARY KEY,
    slot_id BIGINT,
    applicant_id BIGINT,
    status VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    listing_id BIGINT,
    user_id BIGINT,
    body TEXT,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS verifications (
    id BIGSERIAL PRIMARY KEY,
    subject_user_id BIGINT,
    property_id BIGINT,
    document_url VARCHAR(255),
    type VARCHAR(255),
    status VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    type VARCHAR(255),
    payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE
);
