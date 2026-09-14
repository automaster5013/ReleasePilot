CREATE TABLE environments (
 id BINARY(16) PRIMARY KEY, service_id BINARY(16) NOT NULL, name VARCHAR(20) NOT NULL,
 cluster_id BINARY(16) NOT NULL, namespace VARCHAR(63) NOT NULL, rollout_name VARCHAR(253) NOT NULL,
 stable_service_name VARCHAR(253) NOT NULL, canary_service_name VARCHAR(253) NOT NULL,
 prometheus_connection_id BINARY(16) NOT NULL, workload_label_selector VARCHAR(2000) NOT NULL,
 default_policy_version_id BINARY(16) NOT NULL, status VARCHAR(30) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, validated_at TIMESTAMP(6) NULL,
 CONSTRAINT fk_env_service FOREIGN KEY(service_id) REFERENCES services(id),
 CONSTRAINT fk_env_cluster FOREIGN KEY(cluster_id) REFERENCES cluster_connections(id),
 CONSTRAINT fk_env_prometheus FOREIGN KEY(prometheus_connection_id) REFERENCES prometheus_connections(id),
 CONSTRAINT uk_env_service_name UNIQUE(service_id,name)
);
CREATE TABLE environment_validation_results (
 id BINARY(16) PRIMARY KEY, environment_id BINARY(16) NOT NULL, check_code VARCHAR(60) NOT NULL,
 outcome VARCHAR(10) NOT NULL, message VARCHAR(500) NOT NULL, details_json JSON NOT NULL,
 checked_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_validation_environment FOREIGN KEY(environment_id) REFERENCES environments(id)
);
CREATE INDEX idx_validation_environment_time ON environment_validation_results(environment_id,checked_at DESC);
