ALTER TABLE releases ADD COLUMN approved_at TIMESTAMP(6) NULL;
ALTER TABLE releases ADD COLUMN finished_at TIMESTAMP(6) NULL;

CREATE TABLE approval_decisions (
 id BINARY(16) PRIMARY KEY, release_id BINARY(16) NOT NULL UNIQUE, decided_by BINARY(16) NOT NULL,
 decision VARCHAR(20) NOT NULL, reason VARCHAR(1000) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
 decided_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_approval_release FOREIGN KEY(release_id) REFERENCES releases(id),
 CONSTRAINT uk_approval_idempotency UNIQUE(decided_by,idempotency_key)
);
