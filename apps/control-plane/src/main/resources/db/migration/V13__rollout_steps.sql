CREATE TABLE rollout_steps (
 id BINARY(16) PRIMARY KEY, execution_id BINARY(16) NOT NULL, step_index INT NOT NULL,
 target_weight INT NOT NULL, minimum_observation_seconds INT NOT NULL, status VARCHAR(20) NOT NULL,
 started_at TIMESTAMP(6) NULL, evaluation_started_at TIMESTAMP(6) NULL, finished_at TIMESTAMP(6) NULL,
 CONSTRAINT fk_rollout_step_execution FOREIGN KEY(execution_id) REFERENCES rollout_executions(id),
 CONSTRAINT uk_rollout_step_index UNIQUE(execution_id, step_index),
 CONSTRAINT chk_rollout_step_weight CHECK(target_weight BETWEEN 1 AND 100),
 CONSTRAINT chk_rollout_step_observation CHECK(minimum_observation_seconds >= 0)
);
