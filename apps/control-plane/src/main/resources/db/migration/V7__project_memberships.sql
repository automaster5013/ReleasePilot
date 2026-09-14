CREATE TABLE project_memberships (
 id BINARY(16) PRIMARY KEY, project_id BINARY(16) NOT NULL, user_id BINARY(16) NOT NULL,
 role VARCHAR(20) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_membership_project FOREIGN KEY(project_id) REFERENCES projects(id),
 CONSTRAINT fk_membership_user FOREIGN KEY(user_id) REFERENCES user_accounts(id),
 CONSTRAINT uk_membership_project_user UNIQUE(project_id,user_id)
);
CREATE INDEX idx_membership_user ON project_memberships(user_id,project_id);
