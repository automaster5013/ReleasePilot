package kr.releasepilot.controlplane.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    private UUID id;

    @Column(name = "project_key", nullable = false, unique = true, length = 63)
    private String key;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
    }

    public static Project create(String key, String name, String description, Instant createdAt) {
        Project project = new Project();
        project.id = UUID.randomUUID();
        project.key = key;
        project.name = name;
        project.description = description;
        project.status = ProjectStatus.ACTIVE;
        project.createdAt = createdAt;
        return project;
    }

    public UUID getId() { return id; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
