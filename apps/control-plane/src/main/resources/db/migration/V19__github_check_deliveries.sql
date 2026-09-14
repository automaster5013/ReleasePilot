CREATE TABLE github_check_deliveries (
    id BINARY(16) NOT NULL PRIMARY KEY,
    release_id BINARY(16) NOT NULL,
    repository_owner VARCHAR(100) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    head_sha VARCHAR(64) NOT NULL,
    external_check_run_id BIGINT NULL,
    desired_status VARCHAR(20) NOT NULL,
    desired_conclusion VARCHAR(30) NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    generation INTEGER NOT NULL,
    attempts INTEGER NOT NULL,
    available_at TIMESTAMP(6) NOT NULL,
    claimed_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(2000) NULL,
    CONSTRAINT uk_github_check_deliveries_release UNIQUE (release_id),
    CONSTRAINT fk_github_check_deliveries_release FOREIGN KEY (release_id) REFERENCES releases (id)
);

CREATE INDEX idx_github_check_delivery_dispatch
    ON github_check_deliveries (delivery_status, available_at);
