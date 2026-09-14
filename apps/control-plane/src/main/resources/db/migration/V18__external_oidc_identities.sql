CREATE TABLE external_identities (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    issuer VARCHAR(500) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    created_at TIMESTAMP(6) NOT NULL,
    last_login_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_external_identities_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT fk_external_identities_user FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_external_identities_user ON external_identities (user_id);
