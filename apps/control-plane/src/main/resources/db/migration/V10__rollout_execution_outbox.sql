CREATE TABLE rollout_executions (
 id BINARY(16) PRIMARY KEY, release_id BINARY(16) NOT NULL UNIQUE, cluster_id BINARY(16) NOT NULL,
 namespace VARCHAR(63) NOT NULL, rollout_name VARCHAR(253) NOT NULL, target_revision VARCHAR(71) NOT NULL,
 status VARCHAR(30) NOT NULL, current_step_index INT NOT NULL, rollout_uid VARCHAR(128) NULL,
 last_observed_resource_version VARCHAR(128) NULL, created_at TIMESTAMP(6) NOT NULL,
 started_at TIMESTAMP(6) NULL, finished_at TIMESTAMP(6) NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_rollout_execution_release FOREIGN KEY(release_id) REFERENCES releases(id),
 CONSTRAINT fk_rollout_execution_cluster FOREIGN KEY(cluster_id) REFERENCES cluster_connections(id)
);

CREATE TABLE outbox_commands (
 id BINARY(16) PRIMARY KEY, aggregate_type VARCHAR(60) NOT NULL, aggregate_id BINARY(16) NOT NULL,
 command_type VARCHAR(60) NOT NULL, payload_json JSON NOT NULL, idempotency_key VARCHAR(128) NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL, attempts INT NOT NULL, available_at TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, processed_at TIMESTAMP(6) NULL, last_error VARCHAR(2000) NULL
);

CREATE INDEX idx_outbox_dispatch ON outbox_commands(status, available_at);
