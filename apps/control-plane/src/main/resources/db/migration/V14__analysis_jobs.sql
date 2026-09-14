CREATE TABLE analysis_jobs (
 id BINARY(16) PRIMARY KEY,
 step_id BINARY(16) NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL,
 attempts INT NOT NULL,
 max_attempts INT NOT NULL,
 available_at TIMESTAMP(6) NOT NULL,
 claimed_at TIMESTAMP(6) NULL,
 window_start TIMESTAMP(6) NOT NULL,
 window_end TIMESTAMP(6) NOT NULL,
 verdict VARCHAR(20) NULL,
 reason_code VARCHAR(60) NULL,
 evidence_json JSON NULL,
 last_error VARCHAR(2000) NULL,
 created_at TIMESTAMP(6) NOT NULL,
 completed_at TIMESTAMP(6) NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_analysis_job_step FOREIGN KEY(step_id) REFERENCES rollout_steps(id)
);

CREATE INDEX idx_analysis_jobs_dispatch ON analysis_jobs(status, available_at);
