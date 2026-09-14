CREATE TABLE user_accounts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_user_accounts_username UNIQUE (username)
);

CREATE TABLE user_roles (
    user_id BINARY(16) NOT NULL,
    role VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);

CREATE TABLE projects (
    id BINARY(16) NOT NULL PRIMARY KEY,
    project_key VARCHAR(63) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_projects_project_key UNIQUE (project_key)
);

CREATE TABLE audit_events (
    id BINARY(16) NOT NULL PRIMARY KEY,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id BINARY(16),
    occurred_at TIMESTAMP(6) NOT NULL,
    correlation_id BINARY(16) NOT NULL,
    payload_json JSON NOT NULL
);

CREATE INDEX idx_audit_events_aggregate
    ON audit_events (aggregate_type, aggregate_id, occurred_at);
