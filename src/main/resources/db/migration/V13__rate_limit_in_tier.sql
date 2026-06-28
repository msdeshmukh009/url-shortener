ALTER TABLE tiers 
ADD COLUMN rate_limit_per_min INTEGER;

UPDATE tiers SET rate_limit_per_min = 5 WHERE name = 'FREE';
UPDATE tiers SET rate_limit_per_min = 100 WHERE name = 'HOBBY';
UPDATE tiers SET rate_limit_per_min = NULL WHERE name = 'ENTERPRISE';