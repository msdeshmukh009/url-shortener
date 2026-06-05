ALTER TABLE users ADD COLUMN tier_id INTEGER REFERENCES tiers(id);

UPDATE users 
SET tier_id = (SELECT id FROM tiers WHERE name = 'HOBBY')
WHERE tier_id IS NULL;

ALTER TABLE users ALTER COLUMN tier_id SET NOT NULL;

CREATE INDEX idx_users_tier_id ON users(tier_id);