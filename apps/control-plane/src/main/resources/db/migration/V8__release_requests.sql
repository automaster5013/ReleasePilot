CREATE TABLE releases (
 id BINARY(16) PRIMARY KEY, service_id BINARY(16) NOT NULL, environment_id BINARY(16) NOT NULL,
 requested_by BINARY(16) NOT NULL, version VARCHAR(100) NOT NULL, change_summary VARCHAR(2000) NOT NULL,
 commit_sha VARCHAR(40) NOT NULL, pipeline_url VARCHAR(1000) NOT NULL, status VARCHAR(30) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL, active_slot BOOLEAN NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_release_service FOREIGN KEY(service_id) REFERENCES services(id),
 CONSTRAINT fk_release_environment FOREIGN KEY(environment_id) REFERENCES environments(id),
 CONSTRAINT uk_release_idempotency UNIQUE(requested_by,idempotency_key),
 CONSTRAINT uk_release_active_environment UNIQUE(environment_id,active_slot)
);
CREATE TABLE release_artifacts (
 id BINARY(16) PRIMARY KEY, release_id BINARY(16) NOT NULL UNIQUE, image_repository VARCHAR(500) NOT NULL,
 image_digest VARCHAR(71) NOT NULL, CONSTRAINT fk_artifact_release FOREIGN KEY(release_id) REFERENCES releases(id)
);
CREATE TABLE policy_snapshots (
 id BINARY(16) PRIMARY KEY, release_id BINARY(16) NOT NULL UNIQUE, source_policy_version_id BINARY(16) NOT NULL,
 definition JSON NOT NULL, checksum VARCHAR(64) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_snapshot_release FOREIGN KEY(release_id) REFERENCES releases(id)
);
