ALTER TABLE environments ADD COLUMN container_name VARCHAR(253) NULL AFTER rollout_name;
UPDATE environments SET container_name = rollout_name WHERE container_name IS NULL;
ALTER TABLE environments MODIFY COLUMN container_name VARCHAR(253) NOT NULL;
