CREATE TABLE policies (
 id BINARY(16) PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL, created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE policy_versions (
 id BINARY(16) PRIMARY KEY, policy_id BINARY(16) NOT NULL, version INTEGER NOT NULL,
 definition JSON NOT NULL, checksum VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL,
 created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_policy_version_policy FOREIGN KEY(policy_id) REFERENCES policies(id),
 CONSTRAINT uk_policy_version UNIQUE(policy_id,version), CONSTRAINT uk_policy_checksum UNIQUE(policy_id,checksum)
);
