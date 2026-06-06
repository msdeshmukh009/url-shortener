CREATE TABLE tiers  (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    can_use_bulk_shorten BOOLEAN NOT NULL 
);