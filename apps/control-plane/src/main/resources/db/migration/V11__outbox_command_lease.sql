ALTER TABLE outbox_commands ADD COLUMN claimed_at TIMESTAMP(6) NULL;
CREATE INDEX idx_outbox_stale_claim ON outbox_commands(status, claimed_at);
