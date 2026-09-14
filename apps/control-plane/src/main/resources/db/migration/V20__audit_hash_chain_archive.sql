ALTER TABLE audit_events ADD COLUMN chain_sequence BIGINT NULL;
ALTER TABLE audit_events ADD COLUMN previous_hash CHAR(64) NULL;
ALTER TABLE audit_events ADD COLUMN event_hash CHAR(64) NULL;
CREATE UNIQUE INDEX uk_audit_events_chain_sequence ON audit_events (chain_sequence);
CREATE UNIQUE INDEX uk_audit_events_event_hash ON audit_events (event_hash);

CREATE TABLE audit_chain_head (
    id INT NOT NULL PRIMARY KEY,
    last_sequence BIGINT NOT NULL,
    last_hash CHAR(64) NOT NULL
);
INSERT INTO audit_chain_head (id,last_sequence,last_hash) VALUES (1,0,'0000000000000000000000000000000000000000000000000000000000000000');

CREATE TABLE audit_archive_deliveries (
    id BINARY(16) NOT NULL PRIMARY KEY,
    audit_event_id BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    available_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6),
    last_error VARCHAR(500),
    CONSTRAINT uk_audit_archive_event UNIQUE (audit_event_id),
    CONSTRAINT fk_audit_archive_event FOREIGN KEY (audit_event_id) REFERENCES audit_events (id)
);
CREATE INDEX idx_audit_archive_pending ON audit_archive_deliveries (status,available_at);
