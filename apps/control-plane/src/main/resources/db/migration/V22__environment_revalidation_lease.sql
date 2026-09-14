ALTER TABLE environments ADD COLUMN validation_started_at TIMESTAMP(6) NULL;
CREATE INDEX idx_environment_revalidation ON environments(status,validated_at,validation_started_at);
