CREATE TABLE authentication_rate_limits (
    rate_key VARCHAR(80) NOT NULL PRIMARY KEY,
    window_start BIGINT NOT NULL,
    attempts INT NOT NULL,
    expires_at BIGINT NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_authentication_rate_limits_expiry ON authentication_rate_limits (expires_at);
