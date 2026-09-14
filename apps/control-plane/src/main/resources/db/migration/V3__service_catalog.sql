CREATE TABLE services (
    id BINARY(16) PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    service_key VARCHAR(63) NOT NULL,
    name VARCHAR(100) NOT NULL,
    repository_url VARCHAR(500) NOT NULL,
    owner VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_services_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT uk_services_project_key UNIQUE (project_id, service_key)
);

CREATE INDEX idx_services_project_created ON services(project_id, created_at DESC);
