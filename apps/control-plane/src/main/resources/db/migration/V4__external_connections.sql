CREATE TABLE cluster_connections (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    api_server VARCHAR(500) NOT NULL,
    allowed_namespaces VARCHAR(2000) NOT NULL,
    secret_ref VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_validated_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_cluster_connections_name UNIQUE (name)
);

CREATE TABLE prometheus_connections (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    secret_ref VARCHAR(255) NULL,
    query_timeout_seconds INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_validated_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_prometheus_connections_name UNIQUE (name)
);
