-- V33: WGS-84 coordinates for maps (Vista §8). Nullable for legacy rows; set on property create.

ALTER TABLE properties
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;
