ALTER TABLE environments
    ADD COLUMN rollout_strategy VARCHAR(20) NOT NULL DEFAULT 'CANARY' AFTER container_name;
