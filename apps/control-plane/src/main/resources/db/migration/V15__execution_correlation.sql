ALTER TABLE rollout_executions ADD COLUMN correlation_id BINARY(16) NULL AFTER release_id;
ALTER TABLE analysis_jobs ADD COLUMN correlation_id BINARY(16) NULL AFTER step_id;
ALTER TABLE outbox_commands ADD COLUMN correlation_id BINARY(16) NULL AFTER aggregate_id;
CREATE INDEX idx_rollout_executions_correlation ON rollout_executions(correlation_id);
CREATE INDEX idx_analysis_jobs_correlation ON analysis_jobs(correlation_id);
CREATE INDEX idx_outbox_commands_correlation ON outbox_commands(correlation_id);
